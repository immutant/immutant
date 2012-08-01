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

package org.immutant.daemons;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;


public class Daemon implements DaemonMBean, Service<Daemon> {
    
    public Daemon(ClassLoader loader, Runnable startFunction, Runnable stopFunction) {
        this.classLoader = loader;
        this.startFunction = startFunction;
        this.stopFunction = stopFunction;
    }

    public void start() {
        this.thread = new Thread( this.startFunction );
        this.thread.setContextClassLoader( this.classLoader );
        this.thread.start();
    }

    public void stop() {
        if (isStarted() && this.stopFunction != null) {
            this.stopFunction.run();
            try {
                this.thread.join( THREAD_TIMEOUT );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if ( this.thread.isAlive() ) {
                log.warn( "Timed out waiting for daemon thread to die" );
            }
            this.thread = null;
        }
    }

    @Override
    public Daemon getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
    
    @Override
    public void start(final StartContext context) throws StartException {
        context.asynchronous();
       
        context.execute(new Runnable() {
            public void run() {
                try {
                    Daemon.this.start();
                    context.complete();
                } catch (Exception e) {
                    context.failed( new StartException( e ) );
                }
            }
        });

    }

    @Override
    public void stop(StopContext context) {
        stop();
    }
    
    public boolean isStarted() {
        return (this.thread != null);
    }

    public boolean isStopped() {
        return !isStarted();
    }

    public String getStatus() throws Exception {
        if (isStarted()) {
            return "STARTED";
        }
        return "STOPPED";
    }

    private int THREAD_TIMEOUT = 20000;
    private Thread thread;
    private ClassLoader classLoader;
    private Runnable startFunction;
    private Runnable stopFunction;

    static final Logger log = Logger.getLogger( "org.immutant.daemons" );
}
