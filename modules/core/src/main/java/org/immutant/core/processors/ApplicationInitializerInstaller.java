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

import org.immutant.core.ApplicationInitializer;
import org.immutant.core.ClojureMetaData;
import org.immutant.core.as.CoreServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController.Mode;

public class ApplicationInitializerInstaller implements DeploymentUnitProcessor {
    
    public ApplicationInitializerInstaller() {
    }

    /**
     * @param phaseContext
     *
     * @throws DeploymentUnitProcessingException
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        ClojureMetaData metaData = unit.getAttachment(ClojureMetaData.ATTACHMENT_KEY);

        if (metaData == null) {
            return;
        }

        ApplicationInitializer service = 
            new ApplicationInitializer(unit.getAttachment(ClojureMetaData.FULL_APP_CONFIG),
                                       unit.getAttachment(ClojureMetaData.LEIN_PROJECT),
                                       metaData);

        phaseContext.getServiceTarget()
            .addService(CoreServices.appInitializer(unit), service)
            .addDependency(CoreServices.runtime(unit), service.getClojureRuntimeInjector())
            .setInitialMode(Mode.ACTIVE)    
            .install();
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
        
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
