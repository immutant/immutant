/*
 * Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

package org.immutant.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;

import javax.jms.XAConnection;

import org.immutant.core.as.CoreServices;
import org.immutant.messaging.as.MessagingServices;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.core_extensions.AtRuntimeInstaller;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;


public class MessageProcessorGroupizer extends AtRuntimeInstaller<MessageProcessorGroupizer> {

    public MessageProcessorGroupizer(DeploymentUnit unit) {
        super( unit );
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public MessageProcessorGroup createGroup(final String destinationName, final boolean singleton, final int concurrency, 
            final boolean durable, final String handlerName, final XAConnection connection, final Object setupHandler, 
            final Object startCallback) {
        final String name = destinationName + "." + URLEncoder.encode(handlerName);
        final ServiceName serviceName = MessagingServices.messageProcessor( getUnit(),  name );
        final MessageProcessorGroup group = new MessageProcessorGroup( getUnit().getServiceRegistry(), serviceName,
                destinationName, connection, setupHandler, startCallback );

        group.setConcurrency( concurrency );
        group.setDurable( durable );
        group.setName( name );
        
        rememberGroup( destinationName, serviceName );
                
        replaceService( serviceName,
                new Runnable() {
            public void run() {
                ServiceBuilder builder = build( serviceName, group, singleton );

                ServiceName javaContext = ContextNames.JAVA_CONTEXT_SERVICE_NAME;

                builder.addDependency( CoreServices.runtime( getUnit() ), group.getClojureRuntimeInjector() )
                .addDependency( javaContext.append( "ConnectionFactory" ), group.getConnectionFactoryInjector() )
                .addDependency( javaContext.append( DestinationUtils.cleanServiceName( destinationName ) ), group.getDestinationInjector() )
                .install();

            }
        } );
        
        installMBean( serviceName, "immutant.messaging", group );
        
        return group;
    }

    @SuppressWarnings("rawtypes")
    public void removeGroupsFor(String destinationName) {
        List<ServiceName> groups = installedGroupsFor( destinationName ); 
        if (groups != null) {
            for (ServiceName each : groups) {
                ServiceController service = getUnit().getServiceRegistry().getService( each );
                if (service != null) {
                    ((MessageProcessorGroup)service.getValue()).remove();
                }
            }
            this.installedGroups.remove( destinationName );
        }
    }
    
    public List<ServiceName> installedGroupsFor(String destinationName) {
        return this.installedGroups.get( destinationName ); 
    }
    
    protected void rememberGroup(String destinationName, ServiceName serviceName) {
        List<ServiceName> groups = this.installedGroups.get( destinationName );
        if (groups == null) {
            groups = new ArrayList<ServiceName>();
            this.installedGroups.put( destinationName, groups );
        }
        groups.add( serviceName );
    }
    
    protected Map<String, List<ServiceName>> installedGroups = new HashMap<String, List<ServiceName>>();
}
