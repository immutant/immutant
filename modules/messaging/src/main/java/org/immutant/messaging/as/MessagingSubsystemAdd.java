/*
 * Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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

package org.immutant.messaging.as;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.List;

import org.immutant.messaging.processors.DestinationizerInstaller;
import org.immutant.messaging.processors.MessageListenerFactoryInstaller;
import org.immutant.messaging.processors.MessageProcessorGroupizerInstaller;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.messaging.destinations.HornetQStartupPoolService;
import org.projectodd.polyglot.messaging.processors.ApplicationNamingContextBindingProcessor;

class MessagingSubsystemAdd extends AbstractBoottimeAddStepHandler {

    @Override
    protected void populateModel(ModelNode operation, ModelNode model) {
        model.setEmptyObject();
    }

    @Override
    protected void performBoottime(final OperationContext context, ModelNode operation, ModelNode model,
            ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) throws OperationFailedException {

        context.addStep( new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {
                addDeploymentProcessors( processorTarget, context.getServiceTarget() );
            }
        }, OperationContext.Stage.RUNTIME );

        addHornetQStartupPoolService( context, verificationHandler, newControllers );
    }

    protected void addDeploymentProcessors(final DeploymentProcessorTarget processorTarget, ServiceTarget globalTarget) {

        processorTarget.addDeploymentProcessor( MessagingExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, 3, new MessagingDependenciesProcessor() );

        processorTarget.addDeploymentProcessor( MessagingExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 12, new ApplicationNamingContextBindingProcessor() );
        processorTarget.addDeploymentProcessor( MessagingExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 200, new DestinationizerInstaller(globalTarget) );
        processorTarget.addDeploymentProcessor( MessagingExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 201, new MessageProcessorGroupizerInstaller() );
        processorTarget.addDeploymentProcessor( MessagingExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 202, new MessageListenerFactoryInstaller() );  

    }

    protected void addHornetQStartupPoolService(final OperationContext context, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) {
        final ServiceName hornetQServiceName = org.jboss.as.messaging.MessagingServices.getHornetQServiceName( "default" );
        final ServiceName serviceName = HornetQStartupPoolService.getServiceName( hornetQServiceName );
        HornetQStartupPoolService service = new HornetQStartupPoolService();
        try {
            newControllers.add( context.getServiceTarget().addService( serviceName, service )
                                .addListener( verificationHandler )
                                .setInitialMode( Mode.ON_DEMAND )
                                .install() );
        } catch (DuplicateServiceException ignored) {
            // can happen when overlaid with TorqueBox
        }
    }
    
    protected ServiceName getJMSConnectionFactoryServiceName() {
        return ContextNames.JAVA_CONTEXT_SERVICE_NAME.append( "ConnectionFactory" );
    }

    static ModelNode createOperation(ModelNode address) {
        final ModelNode subsystem = new ModelNode();
        subsystem.get( OP ).set( ADD );
        subsystem.get( OP_ADDR ).set( address );
        return subsystem;
    }

    public MessagingSubsystemAdd() {
    }

    static final MessagingSubsystemAdd ADD_INSTANCE = new MessagingSubsystemAdd();
    static final Logger log = Logger.getLogger( "org.immutant.messaging.as" );

}
