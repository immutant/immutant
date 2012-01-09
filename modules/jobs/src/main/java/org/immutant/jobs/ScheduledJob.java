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

import java.text.ParseException;
import java.util.concurrent.Callable;

import org.projectodd.polyglot.jobs.BaseScheduledJob;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

public class ScheduledJob extends BaseScheduledJob implements ScheduledJobMBean {
    	
    public ScheduledJob(String group, String name, String cronExpression, boolean singleton) {
        super( ClojureJob.class, group, name, "", cronExpression, singleton );
    }
    
    @Override 
    public synchronized void start() throws ParseException, SchedulerException {
        getJobScheduler().addJob( getName(), new ClojureJob( this.fn ) );
        super.start();
    }
  
    @Override 
    public Scheduler getScheduler() {
        return getJobScheduler().getScheduler();
    }
    
    protected JobScheduler getJobScheduler() {
        // the singletonJobScheduler will be null if we aren't in a cluster, 
        // and in that case the singleton option has no meaning  
        if (isSingleton() && this.singletonJobScheduler != null) {
            return this.singletonJobScheduler;
        } else {
            return this.nonSingletonJobScheduler;
        }
    }
    
    public void setFn(Callable<?> fn) {
        this.fn = fn;
    }

    public void setSingletonJobScheduler(JobScheduler singletonJobScheduler) {
        this.singletonJobScheduler = singletonJobScheduler;
    }

    public void setNonSingletonJobScheduler(JobScheduler jobScheduler) {
        this.nonSingletonJobScheduler = jobScheduler;
    }

    private Callable<?> fn;
    private JobScheduler singletonJobScheduler;
    private JobScheduler nonSingletonJobScheduler;
        
}
