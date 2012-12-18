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

import org.immutant.runtime.ClojureRuntime;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.jobs.BaseJobScheduler;
import org.projectodd.polyglot.jobs.BaseScheduledJob;
import org.quartz.SchedulerException;

public class ScheduledJob extends BaseScheduledJob implements ScheduledJobMBean {

    @SuppressWarnings("rawtypes")
    public ScheduledJob(Callable handler, String group, String name, String cronExpression, boolean singleton) {
        super( ClojureJob.class, group, name, "", cronExpression, singleton );
        this.handler = handler;
    }

    @Override
    public void start() throws ParseException, SchedulerException {
        JobScheduler scheduler = (JobScheduler)((InjectedValue<BaseJobScheduler>)getJobSchedulerInjector()).getValue();
        ClojureRuntime runtime = this.clojureRuntimeInjector.getValue();
        scheduler.addJob( getName(), getGroup(), new ClojureJob( runtime, this.handler ) );
        
        super.start();
    }
    
    public Injector<ClojureRuntime> getClojureRuntimeInjector() {
        return this.clojureRuntimeInjector;
    }
    
    @SuppressWarnings("rawtypes")
    private Callable handler;
    private InjectedValue<ClojureRuntime> clojureRuntimeInjector = new InjectedValue<ClojureRuntime>();
    
}
