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

package org.immutant.core.processors;

import org.immutant.core.ClojureMetaData;
import org.immutant.core.ClojureRuntime;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

public abstract class RegisteringProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        if (!unit.hasAttachment( ClojureMetaData.ATTACHMENT_KEY )) {
            return;
        }
        
        ClojureRuntime runtime = unit.getAttachment( ClojureRuntime.ATTACHMENT_KEY );
        RegistryEntry entry = registryEntry( phaseContext );
        runtime.invoke( "immutant.registry/put", entry.key, entry.value );
    }

    public abstract RegistryEntry registryEntry( DeploymentPhaseContext context );
    
    @Override
    public void undeploy(DeploymentUnit arg0) {
    }
    
    
    protected class RegistryEntry {
        public String key;
        public Object value;
        
        public RegistryEntry(String key, Object value) {
            this.key = key;
            this.value = value;
        }
   }
}
