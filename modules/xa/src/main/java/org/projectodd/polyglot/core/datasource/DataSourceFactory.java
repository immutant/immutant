/*
 * Copyright 2008-2012 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.projectodd.polyglot.core.datasource;

import java.sql.Driver;
import java.util.Map;
import javax.sql.DataSource;

import org.jboss.as.connector.ConnectorServices;
import org.jboss.as.connector.registry.DriverRegistry;
import org.jboss.as.connector.subsystems.datasources.DataSourceReferenceFactoryService;
import org.jboss.as.connector.subsystems.datasources.ModifiableXaDataSource;
import org.jboss.as.connector.subsystems.datasources.XaDataSourceService;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.naming.service.NamingService;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.jca.common.api.metadata.common.CommonXaPool;
import org.jboss.jca.common.api.metadata.common.FlushStrategy;
import org.jboss.jca.common.api.metadata.common.Recovery;
import org.jboss.jca.common.api.metadata.ds.Statement;
import org.jboss.jca.common.api.metadata.ds.TimeOut;
import org.jboss.jca.common.api.metadata.ds.TransactionIsolation;
import org.jboss.jca.common.api.metadata.ds.Validation;
import org.jboss.jca.common.metadata.common.CommonXaPoolImpl;
import org.jboss.jca.core.api.connectionmanager.ccm.CachedConnectionManager;
import org.jboss.jca.core.api.management.ManagementRepository;
import org.jboss.jca.core.spi.transaction.TransactionIntegration;
import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.core.datasource.db.Adapter;


public class DataSourceFactory {

    public DataSourceFactory(DeploymentUnit unit, ServiceTarget target) {
        this.unit = unit;
        this.target = target;
    }

    public DataSource create(String name, Map<String,Object> spec) {

        final String jndiName = DataSourceServices.jndiName( unit, name );
        final ServiceName dataSourceServiceName = DataSourceServices.datasourceName( unit, name );

        try {
            Adapter adapter = Adapter.find(spec);
            ModifiableXaDataSource config = createConfig( name, spec, adapter);
            XaDataSourceService service = new XaDataSourceService( jndiName );

            service.getDataSourceConfigInjector().inject( config );
            target.addService( dataSourceServiceName, service )
                .addDependency(ConnectorServices.JDBC_DRIVER_REGISTRY_SERVICE, DriverRegistry.class, service.getDriverRegistryInjector())
                .addDependency(DataSourceServices.driverName( unit, adapter.getId() ), Driver.class, service.getDriverInjector())
                .addDependency(ConnectorServices.MANAGEMENT_REPOSITORY_SERVICE, ManagementRepository.class, service.getManagementRepositoryInjector())
                .addDependency(ConnectorServices.TRANSACTION_INTEGRATION_SERVICE, TransactionIntegration.class, service.getTransactionIntegrationInjector())
                .addDependency(NamingService.SERVICE_NAME)
                .addDependency(ConnectorServices.CCM_SERVICE, CachedConnectionManager.class, service.getCcmInjector())
                .install();

            final DataSourceReferenceFactoryService referenceFactoryService = new DataSourceReferenceFactoryService();
            final ServiceName referenceFactoryServiceName = DataSourceReferenceFactoryService.SERVICE_NAME_BASE.append( jndiName );
            final ServiceBuilder<?> referenceBuilder = target.addService( referenceFactoryServiceName,
                    referenceFactoryService ).addDependency( dataSourceServiceName, DataSource.class,
                    referenceFactoryService.getDataSourceInjector() );

            referenceBuilder.setInitialMode( Mode.ACTIVE );
            referenceBuilder.install();

            final ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor( jndiName );
            final BinderService binderService = new BinderService( bindInfo.getBindName() );
            final ServiceBuilder<?> binderBuilder = target.addService( bindInfo.getBinderServiceName(), binderService )
                .addDependency( referenceFactoryServiceName, ManagedReferenceFactory.class, binderService.getManagedObjectInjector() )
                .addDependency( bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binderService.getNamingStoreInjector() )
                .addListener( new AbstractServiceListener<Object>() {
                        public void transition(final ServiceController<? extends Object> controller, final ServiceController.Transition transition) {
                            switch (transition) {
                            case STARTING_to_UP: {
                                log.infof( "Bound data source [%s]", jndiName );
                                break;
                            }
                            case START_REQUESTED_to_DOWN: {
                                log.infof( "Unbound data source [%s]", jndiName );
                                break;
                            }
                            case REMOVING_to_REMOVED: {
                                log.debugf( "Removed JDBC Data-source [%s]", jndiName );
                                break;
                            }
                            }
                        }
                    } );

            binderBuilder.setInitialMode( Mode.ACTIVE );
            binderBuilder.install();

            return service.getValue();
        } catch (Exception e) {
            throw new RuntimeException( e );
        }

    }

    protected ModifiableXaDataSource createConfig(String name, Map<String,Object> spec, Adapter adapter) throws Exception {
        TransactionIsolation transactionIsolation = null;
        TimeOut timeOut = null;
        Statement statement = null;
        Validation validation = adapter.getValidationFor( spec );
        String urlDelimiter = null;
        String urlSelectorStrategyClassName = null;
        boolean useJavaContext = false;
        String poolName = unit.getName() + "." + name;
        boolean enabled = true;
        boolean spy = false;
        boolean useCcm = false;
        String newConnectionSql = null;
        Recovery recovery = null;

        return new ModifiableXaDataSource(transactionIsolation,
                                          timeOut,
                                          adapter.getSecurityFor( spec ),
                                          statement,
                                          validation,
                                          urlDelimiter,
                                          urlSelectorStrategyClassName,
                                          useJavaContext,
                                          poolName,
                                          enabled,
                                          DataSourceServices.jndiName( unit, name ),
                                          spy,
                                          useCcm,
                                          adapter.getPropertiesFor( spec ),
                                          adapter.getDataSourceClassName(),
                                          adapter.getId(),
                                          newConnectionSql,
                                          createPool((Integer) spec.get("pool")),
                                          recovery);
    }

    protected CommonXaPool createPool(int poolSize) throws Exception {
        Integer minPoolSize = 0;
        Integer maxPoolSize = poolSize;
        Boolean prefill = false;
        Boolean useStrictMin = false;
        FlushStrategy flushStrategy = FlushStrategy.FAILING_CONNECTION_ONLY;
        Boolean isSameRmOverride = false;
        Boolean interleaving = false;
        Boolean padXid = false;
        Boolean wrapXaDataSource = false;
        Boolean noTxSeparatePool = false;

        return new CommonXaPoolImpl(minPoolSize,
                                    maxPoolSize,
                                    prefill,
                                    useStrictMin,
                                    flushStrategy,
                                    isSameRmOverride,
                                    interleaving,
                                    padXid,
                                    wrapXaDataSource,
                                    noTxSeparatePool);
    }

    private static final Logger log = Logger.getLogger( DataSourceFactory.class );
    private DeploymentUnit unit;
    private ServiceTarget target;

}
