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

import org.jboss.as.server.deployment.*;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;

import org.immutant.core.Closer;
import org.immutant.core.as.CoreServices;


public class CloserInstaller implements DeploymentUnitProcessor {

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        Closer service = new Closer();
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        phaseContext.getServiceTarget().addService(CoreServices.HOUSEKEEPER, service)
            .setInitialMode(Mode.ACTIVE)
            .install();
    }

    public void undeploy(DeploymentUnit context) {

    }

}
