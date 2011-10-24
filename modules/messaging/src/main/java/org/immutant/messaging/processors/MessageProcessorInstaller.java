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

package org.immutant.messaging.processors;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;

import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;

import org.immutant.core.ClojureMetaData;
import org.immutant.core.ClojureRuntime;
import org.immutant.messaging.MessageProcessorGroup;
import org.immutant.messaging.MessageProcessorGroupMBean;
import org.immutant.messaging.MessageProcessorMetaData;
import org.immutant.messaging.as.MessagingServices;


public class MessageProcessorInstaller implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        ClojureMetaData appMetaData = unit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );
        if (appMetaData != null) {
            List<List> processors = (List<List>) appMetaData.getList( "processors" );
            if (processors != null) { 
                for (List metadata : processors) {
                    String destination = (String) metadata.get(0);
                    Runnable handler = (Runnable) metadata.get(1);
                    Map<String,?> opts = (Map<String,?>) metadata.get(2);
                    deploy( phaseContext, new MessageProcessorMetaData( destination, handler, opts ) );
                }
            }
        }
    }

    protected void deploy(DeploymentPhaseContext phaseContext, final MessageProcessorMetaData metaData) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        final String name = metaData.getName();
        ClojureRuntime runtime = unit.getAttachment( ClojureRuntime.ATTACHMENT_KEY );

        ServiceName baseServiceName = MessagingServices.messageProcessor( unit, name );
        MessageProcessorGroup service = new MessageProcessorGroup( phaseContext.getServiceRegistry(), baseServiceName, metaData.getDestinationName() );
        service.setRuntime( runtime );
        service.setFunction( metaData.getHandler() );
        service.setConcurrency( metaData.getConcurrency() );
        service.setDurable( metaData.getDurable() );
        service.setMessageSelector( metaData.getFilter() );
        service.setName( metaData.getName() );

        phaseContext.getServiceTarget().addService( baseServiceName, service )
            .addDependency( getConnectionFactoryServiceName(), ManagedReferenceFactory.class, service.getConnectionFactoryInjector() )
            .addDependency( getDestinationServiceName( metaData.getDestinationName() ), ManagedReferenceFactory.class, service.getDestinationInjector() )
            .setInitialMode( Mode.ACTIVE )
            .install();
        
        String mbeanName = ObjectNameFactory.create( "immutant.messaging.processors", new Hashtable<String, String>() {
            {
                put( "app", "FIXME" );
                put( "name", metaData.getName() );
            }
        } ).toString();

        MBeanRegistrationService<MessageProcessorGroupMBean> mbeanService = new MBeanRegistrationService<MessageProcessorGroupMBean>( mbeanName );
        phaseContext.getServiceTarget().addService( baseServiceName.append( "mbean" ), mbeanService )
                .addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() )
                .addDependency( baseServiceName, MessageProcessorGroupMBean.class, mbeanService.getValueInjector() )
                .setInitialMode( Mode.PASSIVE )
                .install();
    }

    protected ServiceName getConnectionFactoryServiceName() {
        return ContextNames.JAVA_CONTEXT_SERVICE_NAME.append( "ConnectionFactory" );
    }

    protected ServiceName getDestinationServiceName(String destination) {
        return ContextNames.JAVA_CONTEXT_SERVICE_NAME.append( DestinationUtils.getServiceName( destination ) );
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

}
