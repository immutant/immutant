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

package org.immutant.web.as;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.List;

import org.immutant.web.ring.processors.RingApplicationRecognizer;
import org.immutant.web.ring.processors.RingWebApplicationInstaller;
import org.immutant.web.ring.processors.WebContextRegisteringProcessor;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.projectodd.polyglot.web.processors.VirtualHostInstaller;
import org.projectodd.polyglot.web.processors.WebApplicationDefaultsProcessor;

class WebSubsystemAdd extends AbstractBoottimeAddStepHandler {
    
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) {
        model.setEmptyObject();
    }
    
    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model,
                                   ServiceVerificationHandler verificationHandler,
                                   List<ServiceController<?>> newControllers) throws OperationFailedException {
        
        context.addStep( new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {
                addDeploymentProcessors( processorTarget );
            }
        }, OperationContext.Stage.RUNTIME );
        
    }

    protected void addDeploymentProcessors(final DeploymentProcessorTarget processorTarget) {
        processorTarget.addDeploymentProcessor( WebExtension.SUBSYSTEM_NAME, Phase.PARSE, 0, new RingApplicationRecognizer() );
        processorTarget.addDeploymentProcessor( WebExtension.SUBSYSTEM_NAME, Phase.PARSE, 60, new WebApplicationDefaultsProcessor() );
        processorTarget.addDeploymentProcessor( WebExtension.SUBSYSTEM_NAME, Phase.PARSE, 70, new RingWebApplicationInstaller() );
        
        processorTarget.addDeploymentProcessor( WebExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, 1, new WebDependenciesProcessor() );
        
        processorTarget.addDeploymentProcessor( WebExtension.SUBSYSTEM_NAME, Phase.INSTALL, 2100, new VirtualHostInstaller() );
        processorTarget.addDeploymentProcessor( WebExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT + 1, new WebContextRegisteringProcessor() );
    }


    static ModelNode createOperation(ModelNode address) {
        final ModelNode subsystem = new ModelNode();
        subsystem.get( OP ).set( ADD );
        subsystem.get( OP_ADDR ).set( address );
        return subsystem;
    }

    static final WebSubsystemAdd ADD_INSTANCE = new WebSubsystemAdd();
    static final Logger log = Logger.getLogger( "org.immutant.web.as" );

}
