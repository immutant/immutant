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
import org.immutant.runtime.ClojureRuntime;
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
        
        ClojureRuntime runtime = unit.getAttachment( ClojureRuntime.ATTACHMENT_KEY );
        
        runtime.invoke( "immutant.runtime/initialize", metaData.getInitFunction(), metaData.getConfig() );
        
    }

    @Override
    public void undeploy(DeploymentUnit arg0) {
        // TODO Auto-generated method stub
        
    }

}
