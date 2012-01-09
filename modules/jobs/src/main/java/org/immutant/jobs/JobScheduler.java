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

package org.immutant.jobs;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.immutant.core.ClojureRuntime;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.jobs.BaseJobScheduler;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

public class JobScheduler extends BaseJobScheduler implements JobFactory {

	public JobScheduler(String name) {
		super( name );
	}
	
    public void start() throws IOException, SchedulerException {
        setJobFactory( this );
        super.start();
    }
     
    @Override
    public Job newJob(TriggerFiredBundle bundle) throws SchedulerException {
        JobDetail jobDetail = bundle.getJobDetail();
        
        return this.jobs.get( jobDetail.getName() );
    }
    
    public void addJob(String jobName, ClojureJob job) {
        this.jobs.put( jobName, job );
    }
    
    private Map<String, ClojureJob> jobs = new HashMap<String, ClojureJob>();

	private static final Logger log = Logger.getLogger( "org.immutant.jobs" );
}
