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

package org.immutant.jobs.processors;

import org.immutant.core.ClojureMetaData;
import org.immutant.jobs.JobScheduler;
import org.immutant.jobs.as.JobsServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.core.processors.ClusterAwareProcessor;
import org.projectodd.polyglot.hasingleton.HASingleton;
import org.projectodd.polyglot.jobs.BaseJobScheduler;

/**
 * Creates a JobScheduler service if there are any job meta data
 */
public class JobSchedulerInstaller extends ClusterAwareProcessor {

    public JobSchedulerInstaller() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        
        if (!unit.hasAttachment( ClojureMetaData.ATTACHMENT_KEY )) {
            return;
        }
        
        if (isClustered( phaseContext )) {
            log.debug( "Deploying clustered scheduler: " + unit );
            buildScheduler( phaseContext, true );
            buildScheduler( phaseContext, false );
        } else {
            log.debug( "Deploying scheduler: " + unit );
            buildScheduler( phaseContext, false );
        }
    }

    @Override
    public void undeploy(DeploymentUnit unit) {

    }

    private void buildScheduler(DeploymentPhaseContext phaseContext, boolean singleton) {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        ServiceName serviceName = JobsServices.jobScheduler( unit, singleton );

        JobScheduler scheduler = new JobScheduler( "JobScheduler$" + unit.getName() );

        ServiceBuilder<BaseJobScheduler> builder = phaseContext.getServiceTarget().addService( serviceName, scheduler );
        
        if (singleton) {
            builder.addDependency( HASingleton.serviceName( unit ) );
            builder.setInitialMode( Mode.PASSIVE );
        } else {
            builder.setInitialMode( Mode.ACTIVE );
        }

        builder.install();
    }


    private static final Logger log = Logger.getLogger( "org.immutant.jobs" );

}
