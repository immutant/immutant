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

import java.util.concurrent.Callable;

import org.immutant.runtime.ClojureRuntime;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

public class ClojureJob implements Job, StatefulJob {

    @SuppressWarnings("rawtypes")
    public ClojureJob(ClojureRuntime runtime, Callable fn) {
        this.runtime = runtime;
        this.fn = fn;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            this.runtime.invoke( this.fn, context );
        } catch (Exception e) {
            throw new JobExecutionException( e );
        }  
    }

    private ClojureRuntime runtime;
    @SuppressWarnings("rawtypes")
    private Callable fn;
}
