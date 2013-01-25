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

import java.io.File;

import org.immutant.bootstrap.ApplicationBootstrapUtils;
import org.immutant.core.ClojureMetaData;
import org.immutant.core.Timer;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.logging.Logger;

public class FullAppConfigLoadingProcessor implements DeploymentUnitProcessor {
    
    public FullAppConfigLoadingProcessor() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        ClojureMetaData metaData = deploymentUnit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );

        if (metaData == null) {
            return;
        }

        Timer t = new Timer("reading full app config");
        ResourceRoot resourceRoot = deploymentUnit.getAttachment( Attachments.DEPLOYMENT_ROOT );
        File root;
        File descriptor = deploymentUnit.getAttachment( ClojureMetaData.DESCRIPTOR_FILE );
        try {
            root = resourceRoot.getRoot().getPhysicalFile();
            metaData.setConfig( ApplicationBootstrapUtils.readFullAppConfig( descriptor, root ) );
            deploymentUnit.putAttachment( ClojureMetaData.FULL_APP_CONFIG, 
                    ApplicationBootstrapUtils.readFullAppConfigAsString( descriptor, root ) );
            deploymentUnit.putAttachment( ClojureMetaData.LEIN_PROJECT, 
                    ApplicationBootstrapUtils.readProjectAsString( descriptor, root ) );
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException( e );
        }
        t.done();

    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
    
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
