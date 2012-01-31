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

package org.immutant.core.processors;

import org.immutant.core.ClojureMetaData;
import org.immutant.core.ClojureRuntime;
import org.immutant.core.VFSStrippingClassLoader;
import org.immutant.core.as.CoreServices;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceController.Mode;

/**
 * Attaches a ClojureRuntime to the deployment. There is one ClojureRuntime per app.
 * 
 */
public class ClojureRuntimeInstaller implements DeploymentUnitProcessor {
    
    public ClojureRuntimeInstaller() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        if (!deploymentUnit.hasAttachment( ClojureMetaData.ATTACHMENT_KEY )) {
            return;
        }
        
        Module module = deploymentUnit.getAttachment( Attachments.MODULE );
        ClassLoader loader;
        if (module != null) {
            loader = module.getClassLoader();
        } else {
            // this won't happen in production, but helps testing    
            loader = this.getClass().getClassLoader(); 
        }
        ClojureRuntime runtime = new ClojureRuntime( new VFSStrippingClassLoader( loader ) );
        runtime.invoke( "immutant.registry/set-msc-registry", deploymentUnit.getServiceRegistry() );
        
        deploymentUnit.putAttachment( ClojureRuntime.ATTACHMENT_KEY, runtime );
        
        phaseContext.getServiceTarget().addService(CoreServices.runtime( deploymentUnit ), runtime)
        .setInitialMode(Mode.ACTIVE)    
        .install();
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
        
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
