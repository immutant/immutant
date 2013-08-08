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

package org.immutant.runtime;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.projectodd.shimdandy.ClojureRuntimeShim;

public class ClojureRuntimeService implements Service<ClojureRuntimeShim> {
    public static final AttachmentKey<ClojureRuntimeShim> ATTACHMENT_KEY = AttachmentKey.create( ClojureRuntimeShim.class );

    public ClojureRuntimeService(ClojureRuntimeShim runtime) {
        this.runtime = runtime;
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public synchronized void stop(StopContext context) {
        log.info( "Shutting down Clojure runtime for " + this.runtime.getName() );
        runtime.invoke( "immutant.runtime/shutdown" );
    }

    @Override 
    public ClojureRuntimeShim getValue() {
        return this.runtime;
    }

    private ClojureRuntimeShim runtime;
    static final Logger log = Logger.getLogger( "org.immutant.runtime" );
}
