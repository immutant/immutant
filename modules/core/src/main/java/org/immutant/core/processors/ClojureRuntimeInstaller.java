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

package org.immutant.core.processors;

import org.immutant.common.ClassLoaderUtils;
import org.immutant.core.ClojureMetaData;
import org.immutant.core.Timer;
import org.immutant.core.as.CoreServices;
import org.immutant.runtime.ClojureRuntime;
import org.immutant.runtime.ClojureRuntimeService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
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
       Timer t = new Timer("creating clojure runtime");
       ClassLoader loader = ClassLoaderUtils.getModuleLoader( deploymentUnit );
       
        ClojureRuntime runtime = ClojureRuntime.newRuntime( loader, deploymentUnit.getName() );
        deploymentUnit.putAttachment( ClojureRuntimeService.ATTACHMENT_KEY, runtime );
        
        runtime.invoke( "immutant.registry/set-msc-registry", deploymentUnit.getServiceRegistry() );
        runtime.invoke( "immutant.registry/put", "clojure-runtime", runtime );
        
        phaseContext.getServiceTarget().addService(  CoreServices.runtime( deploymentUnit ), new ClojureRuntimeService(runtime) )
        .setInitialMode(Mode.ACTIVE)    
        .install();
        t.done();
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
        
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
