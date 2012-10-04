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
import org.immutant.core.Timer;
import org.immutant.runtime.ClojureRuntime;
import org.immutant.runtime.ClojureRuntimeService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

public class ApplicationInitializer implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext context) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = context.getDeploymentUnit();
        
        ClojureMetaData metaData = unit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );
        
        if (metaData == null) {
            return;
        }
        
        ClojureRuntime runtime = unit.getAttachment( ClojureRuntimeService.ATTACHMENT_KEY );
        Timer t = new Timer("setting app config");
        runtime.invoke( "immutant.runtime/set-app-config", 
                unit.getAttachment( ClojureMetaData.FULL_APP_CONFIG ), 
                unit.getAttachment( ClojureMetaData.LEIN_PROJECT ) );
        t.done();
        t = new Timer("initializing app");
        runtime.invoke( "immutant.runtime/initialize", metaData.getInitFunction(), metaData.getConfig() );
        t.done();
    }

    @Override
    public void undeploy(DeploymentUnit arg0) {
        // TODO Auto-generated method stub
        
    }

}
