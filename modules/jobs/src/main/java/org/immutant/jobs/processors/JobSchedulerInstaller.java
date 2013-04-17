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

package org.immutant.jobs.processors;

import org.immutant.jobs.JobScheduler;
import org.immutant.jobs.as.JobsServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceController.Mode;


public class JobSchedulerInstaller implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext context) {
        DeploymentUnit unit = context.getDeploymentUnit();

        JobScheduler scheduler = new JobScheduler("JobScheduler$" + unit.getName());

        context.getServiceTarget()
            .addService(JobsServices.scheduler(unit), scheduler)
            .setInitialMode(Mode.ON_DEMAND)
            .install();
    }

    @Override
    public void undeploy(DeploymentUnit unit) {
    }
}
