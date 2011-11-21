/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

package org.immutant.daemons.processors;

import java.util.Hashtable;
import java.util.List;

import javax.management.MBeanServer;

import org.immutant.core.ClojureRuntime;
import org.immutant.daemons.Daemon;
import org.immutant.daemons.DaemonMBean;
import org.immutant.daemons.DaemonMetaData;
import org.immutant.daemons.DaemonStart;
import org.immutant.daemons.as.DaemonServices;
import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.ImmediateValue;
import org.projectodd.polyglot.core.app.ApplicationMetaData;
import org.projectodd.polyglot.core.util.StringUtil;

public class DaemonInstaller implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        List<DaemonMetaData> allServiceMetaData = unit.getAttachmentList( DaemonMetaData.ATTACHMENTS_KEY );

        for (DaemonMetaData serviceMetaData : allServiceMetaData) {
            deploy( phaseContext, serviceMetaData );
        }
    }

    protected void deploy(DeploymentPhaseContext phaseContext, final DaemonMetaData daemonMetaData) {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        ClojureRuntime runtime = unit.getAttachment( ClojureRuntime.ATTACHMENT_KEY );

        Daemon daemon = new Daemon( runtime, daemonMetaData.getStartFunction(), daemonMetaData.getStopFunction() );
        final DaemonStart daemonStart = new DaemonStart( daemon );
        final ServiceName serviceName = DaemonServices.daemon( unit, daemonMetaData.getName() );

        phaseContext.getServiceTarget().addService(serviceName, daemonStart).setInitialMode(Mode.PASSIVE).install();

        final ApplicationMetaData appMetaData = unit.getAttachment( ApplicationMetaData.ATTACHMENT_KEY );

        String mbeanName = ObjectNameFactory.create( "immutant.daemons", new Hashtable<String, String>() {
                {
                    put( "app", appMetaData.getApplicationName() );
                    put( "name", StringUtil.underscore( daemonMetaData.getName() ) );
                }
            } ).toString();

        MBeanRegistrationService<DaemonMBean> mbeanService = new MBeanRegistrationService<DaemonMBean>( mbeanName, new ImmediateValue<DaemonMBean>( daemon ) );
        phaseContext.getServiceTarget().addService( serviceName.append( "mbean" ), mbeanService )
            .addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() )
            .setInitialMode( Mode.PASSIVE )
            .install(); 
    }

    @Override
    public void undeploy(DeploymentUnit unit) {

    }

    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger( "org.immutant.daemons" );

}
