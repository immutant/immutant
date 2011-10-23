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

package org.immutant.daemons.processors;

import java.util.Map;

import org.immutant.core.ClojureMetaData;
import org.immutant.daemons.DaemonMetaData;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;

public class DaemonConfigurationProcessor implements DeploymentUnitProcessor {

    public DaemonConfigurationProcessor() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        ClojureMetaData appMetaData = deploymentUnit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );
        
        if (appMetaData == null) { 
            return;
        }

        Map<String, ?> daemons = appMetaData.getHash( "daemons" );

        if (daemons == null) {
            return;
        }
        
        for(String daemon : daemons.keySet()) {
            DaemonMetaData metaData = new DaemonMetaData( daemon );
            Map <String, ?> options = (Map<String, Object>)daemons.get( daemon );
            String startFunction = (String)options.get( "start" );
            if (startFunction == null || startFunction.isEmpty()) {
                throw new DeploymentUnitProcessingException( "No start function specified for daemon '" + daemon + "'." );
            }
            metaData.setStartFunction( startFunction );
            metaData.setStopFunction( (String)options.get( "stop" ) );
            //TODO: handle parameters
            deploymentUnit.addToAttachmentList( DaemonMetaData.ATTACHMENTS_KEY, metaData );
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    static final Logger log = Logger.getLogger( "org.immutant.daemons" );
}
