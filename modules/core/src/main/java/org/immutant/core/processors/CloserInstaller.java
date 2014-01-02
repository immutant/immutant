/*
 * Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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

import org.immutant.core.Closer;
import org.immutant.core.as.CoreServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceController.Mode;


public class CloserInstaller extends RegisteringProcessor {

    public RegistryEntry registryEntry(DeploymentPhaseContext context) {
        DeploymentUnit unit = context.getDeploymentUnit();
                
        Closer service = new Closer();
                
        context.getServiceTarget().addService(CoreServices.housekeeper( unit ), service)
                .addDependency(CoreServices.runtime(unit))
                .setInitialMode(Mode.ACTIVE)
                .install();
        
        return new RegistryEntry( "housekeeper", service );
    }

}
