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

package org.immutant.messaging.processors;

import java.util.Map;

import org.immutant.core.ClojureMetaData;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.projectodd.polyglot.messaging.destinations.TopicMetaData;

public class TopicConfigurationProcessor implements DeploymentUnitProcessor {
    
    public TopicConfigurationProcessor() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        ClojureMetaData appMetaData = deploymentUnit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );
        Map<String, ?> topics = appMetaData.getHash( "topics" );
        
        if (appMetaData == null || topics == null) { 
            return;
        }
        
        for(String topic : topics.keySet()) {
            TopicMetaData metaData = new TopicMetaData( topic );
            deploymentUnit.addToAttachmentList( TopicMetaData.ATTACHMENTS_KEY, metaData );
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
        
    static final Logger log = Logger.getLogger( "org.immutant.messaging" );
}
