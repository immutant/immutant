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

package org.immutant.core;

import java.util.*;
import org.jboss.msc.service.*;
import org.jboss.logging.Logger;

public class Closer implements Service<Closer> {

    public void atExit(Runnable runnable) {
        log.info("JC: adding "+runnable);
        tasks.add(runnable);
    }

    public void start(StartContext context) throws StartException {
        log.info("JC: starting");
        tasks = new ArrayList<Runnable>();
    }

    public void stop(StopContext context) {
        log.info("JC: stopping");
        for (Runnable runnable: tasks) {
            try {
                log.info("JC: running "+runnable);
                runnable.run();
            } catch (Throwable ignored) {
                log.error("wtf?", ignored);
            }
        }
    }

    public Closer getValue() {
        return this;
    }

    private List<Runnable> tasks;
    private static final Logger log = Logger.getLogger( Closer.class );
}
