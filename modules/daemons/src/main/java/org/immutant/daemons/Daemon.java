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

package org.immutant.daemons;

import org.immutant.core.ClojureRuntime;

public class Daemon implements DaemonMBean {
    public Daemon(ClojureRuntime runtime, String startFunction, String stopFunction) {
        this.runtime = runtime;
        this.startFunction = startFunction;
        this.stopFunction = stopFunction;
    }

    public void start() {
        this.runtime.load( "immutant/utilities" );
        this.runtime.invoke( "immutant.utilities", "load-and-invoke", this.startFunction /*TODO: handle params */);
        this.started = true;
    }

    public void stop() {
        if (this.stopFunction != null) {
            this.runtime.load( "immutant/utilities" );
            this.runtime.invoke( "immutant.utilities", "load-and-invoke", this.stopFunction );
            this.started = false;
        }
    }

    @Override
    public boolean isStarted() {
        return this.started;
    }

    @Override
    public boolean isStopped() {
        return !isStarted();
    }

    @Override
    public String getStatus() throws Exception {
        if (isStarted()) {
            return "STARTED";
        }
        return "STOPPED";
    }

    public ClojureRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(ClojureRuntime runtime) {
        this.runtime = runtime;
    }

    public String getStartFunction() {
        return startFunction;
    }

    public void setStartFunction(String startFunction) {
        this.startFunction = startFunction;
    }

    public String getStopFunction() {
        return stopFunction;
    }

    public void setStopFunction(String stopFunction) {
        this.stopFunction = stopFunction;
    }



    private boolean started;
    private ClojureRuntime runtime;
    private String startFunction;
    private String stopFunction;

}
