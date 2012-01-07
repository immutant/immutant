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

package org.immutant.core;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public class ClojureRuntimeCloser implements Service<ClojureRuntimeCloser> {

    public ClojureRuntimeCloser(ClojureRuntime runtime) {
        this.runtime = runtime;
    }
    
    public void start(StartContext context) throws StartException {

    }

    public synchronized void stop(StopContext context) {
        log.info( "Shutting down Clojure runtime" );
        this.runtime.shutdown();
    }

    public ClojureRuntimeCloser getValue() {
        return this;
    }

    private ClojureRuntime runtime;
    private static final Logger log = Logger.getLogger( ClojureRuntimeCloser.class );
}
