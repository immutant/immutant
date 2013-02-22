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

package org.immutant.jobs;

import java.util.Date;
import java.util.concurrent.Callable;

import org.immutant.core.HasImmutantRuntimeInjector;
import org.immutant.core.as.CoreServices;
import org.immutant.jobs.as.JobsServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.jobs.BaseJob;


public class JobSchedulizer extends AtRuntimeInstaller<JobSchedulizer> {

    public JobSchedulizer(DeploymentUnit unit) {
        super( unit );
    }

    public JobScheduler createScheduler(boolean singleton) {
        JobScheduler scheduler = new JobScheduler( "JobScheduler$" + getUnit().getName() );
        ServiceName serviceName = JobsServices.scheduler( getUnit(), singleton );

        deploy( serviceName, scheduler, singleton );

        return scheduler;
    }

    @SuppressWarnings("rawtypes")
    public ScheduledJob createJob(Callable handler, String name, String cronExpression, final boolean singleton) {
        final ScheduledJob job = new ScheduledJob( handler,
                                                   getUnit().getName(),
                                                   name,
                                                   cronExpression,
                                                   singleton );

        installJob(job);
        
        return job;
    }

    @SuppressWarnings("rawtypes")
    public AtJob createAtJob(Callable handler, String name, Date startAt, Date endAt, long interval, int repeat, final boolean singleton) {
        final AtJob job = new AtJob( handler,
                                     getUnit().getName(),
                                     name,
                                     singleton );
        job.setStartAt( startAt );
        job.setEndAt( endAt );
        job.setInterval( interval );
        job.setRepeat( repeat );

        installJob( job );

        return job;
    }
    
    private void installJob(final BaseJob job) {
        final ServiceName serviceName = JobsServices.job( getUnit(), job.getName() );

        replaceService( serviceName,
                        new Runnable() {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            public void run() {
                ServiceBuilder builder = build( serviceName, job, false );

                builder.addDependency( CoreServices.runtime( getUnit() ), ((HasImmutantRuntimeInjector)job).getClojureRuntimeInjector() )
                .addDependency( JobsServices.scheduler( getUnit(), job.isSingleton() ), job.getJobSchedulerInjector() )
                .install();

            }
        } );

        installMBean( serviceName, "immutant.jobs", job );
        
    }
    
    private static final Logger log = Logger.getLogger( "org.immutant.jobs" );
}
