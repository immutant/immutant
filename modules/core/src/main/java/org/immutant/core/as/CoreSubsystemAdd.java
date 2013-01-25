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

package org.immutant.core.as;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;

import javax.management.MBeanServer;

import org.immutant.core.Immutant;
import org.immutant.core.ImmutantMBean;
import org.immutant.core.processors.AppDependenciesProcessor;
import org.immutant.core.processors.AppNameRegisteringProcessor;
import org.immutant.core.processors.AppRootRegisteringProcessor;
import org.immutant.core.processors.ApplicationInitializer;
import org.immutant.core.processors.ArchiveRecognizer;
import org.immutant.core.processors.ClojureRuntimeInstaller;
import org.immutant.core.processors.CloserInstaller;
import org.immutant.core.processors.DeploymentDescriptorParsingProcessor;
import org.immutant.core.processors.FullAppConfigLoadingProcessor;
import org.immutant.core.processors.TmpResourceMounterInstaller;
import org.immutant.core.processors.TmpResourceMounterRegisteringInstaller;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.projectodd.polyglot.core.processors.ApplicationExploder;
import org.projectodd.polyglot.core.processors.ArchiveStructureProcessor;
import org.projectodd.polyglot.core.processors.DescriptorRootMountProcessor;

class CoreSubsystemAdd extends AbstractBoottimeAddStepHandler {
    
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
        
        try {
            addImmutantService( context, verificationHandler, newControllers );
        } catch (Exception e) {
            throw new OperationFailedException( e, null );
        }
        
    }

    protected void addDeploymentProcessors(final DeploymentProcessorTarget processorTarget) {
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, 0, new DescriptorRootMountProcessor( Immutant.DESCRIPTOR_SUFFIX ) );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, 0, new ArchiveStructureProcessor( Immutant.ARCHIVE_SUFFIX ) );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT + 10, new ArchiveRecognizer() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT + 20, new DeploymentDescriptorParsingProcessor() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT + 30, new ApplicationExploder() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT + 50, new FullAppConfigLoadingProcessor() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT + 90, new TmpResourceMounterInstaller() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_MOUNT + 100, new AppDependenciesProcessor() );
        
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, 1, new CoreDependenciesProcessor() );
        
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 120, new ClojureRuntimeInstaller() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 130, new CloserInstaller() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 140, new AppNameRegisteringProcessor() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 141, new AppRootRegisteringProcessor() );
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, 142, new TmpResourceMounterRegisteringInstaller() );
        
        processorTarget.addDeploymentProcessor( CoreExtension.SUBSYSTEM_NAME, Phase.INSTALL, 10000, new ApplicationInitializer() );
    }

    @SuppressWarnings("serial")
    protected void addImmutantService(final OperationContext context, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) throws IOException {
        Immutant immutant = new Immutant();
        
        newControllers.add( context.getServiceTarget().addService( CoreServices.IMMUTANT, immutant )
                .setInitialMode( Mode.ACTIVE )
                .addListener( verificationHandler )
                .install() );

        String mbeanName = ObjectNameFactory.create( "Immutant", new Hashtable<String, String>() {
            {
                put( "type", "version" );
            }
        } ).toString();

        MBeanRegistrationService<ImmutantMBean> mbeanService = new MBeanRegistrationService<ImmutantMBean>( mbeanName );
        newControllers.add( context.getServiceTarget().addService( CoreServices.IMMUTANT.append( "mbean" ), mbeanService )
                .addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() )
                .addDependency( CoreServices.IMMUTANT, ImmutantMBean.class, mbeanService.getValueInjector() )
                .addListener( verificationHandler )
                .setInitialMode( Mode.PASSIVE )
                .install() );
    }
    static ModelNode createOperation(ModelNode address) {
        final ModelNode subsystem = new ModelNode();
        subsystem.get( OP ).set( ADD );
        subsystem.get( OP_ADDR ).set( address );
        return subsystem;
    }

    static final CoreSubsystemAdd ADD_INSTANCE = new CoreSubsystemAdd();
    static final Logger log = Logger.getLogger( "org.immutant.core.as" );

}
