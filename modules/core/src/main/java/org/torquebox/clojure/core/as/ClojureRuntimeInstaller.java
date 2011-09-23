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

package org.torquebox.clojure.core.as;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.torquebox.clojure.core.ClojureApplicationMetaData;
import org.torquebox.clojure.core.ClojureRuntime;

/**
 * Handle mounting .clj files and marking them as a DEPLOYMENT_ROOT
 * FIXME: This doesn't handle archives
 * 
 */
public class ClojureRuntimeInstaller implements DeploymentUnitProcessor {
    
    public ClojureRuntimeInstaller() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        ClojureApplicationMetaData metaData = deploymentUnit.getAttachment( ClojureApplicationMetaData.ATTACHMENT_KEY );
        
        if (metaData == null) {
            return;
        }
        
        Module module = deploymentUnit.getAttachment( Attachments.MODULE );
        
        deploymentUnit.putAttachment( ClojureRuntime.ATTACHMENT_KEY, new ClojureRuntime( module.getClassLoader() ) );
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
        
    static final Logger log = Logger.getLogger( "org.torquebox.clojure.core.as" );
}
