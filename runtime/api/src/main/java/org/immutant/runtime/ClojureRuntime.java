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

package org.immutant.runtime;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public abstract class ClojureRuntime implements Service<ClojureRuntime> {
    public static final AttachmentKey<ClojureRuntime> ATTACHMENT_KEY = AttachmentKey.create( ClojureRuntime.class );

    public static ClojureRuntime newRuntime(ClassLoader classLoader, String name) {
        ClojureRuntime runtime;
        try {
            runtime = (ClojureRuntime)classLoader.loadClass( "org.immutant.runtime.impl.ClojureRuntimeImpl" ).newInstance();
        } catch (Exception e) {
            throw new RuntimeException( "Failed to load ClojureRuntimeImpl", e );
        }

        runtime.setClassLoader( classLoader );
        runtime.setName( name );

        return runtime;
    }

    public abstract Object invoke(String namespacedFunction, Object... args);

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public synchronized void stop(StopContext context) {
        log.info( "Shutting down Clojure runtime for " + this.name );
        invoke( "clojure.core/shutdown-agents" );
    }

    @Override 
    public ClojureRuntime getValue() {
        return this;
    }

    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    protected ClassLoader classLoader;
    protected String name;

    static final Logger log = Logger.getLogger( "org.immutant.runtime" );
}
