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

import org.immutant.runtime.ClojureRuntime;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceListener;

@SuppressWarnings("rawtypes")
public class ServiceUpListener implements ServiceListener {

    public ServiceUpListener(ClojureRuntime runtime, Object callback) {
        this.runtime = runtime;
        this.callback = callback;
    }
    
    @Override
    public void transition(ServiceController controller, Transition transition) { 
        if (transition == Transition.STARTING_to_UP) {
            this.runtime.invoke( this.callback );
        }
    }
    
    @Override
    public void listenerAdded(ServiceController controller) { }

    @Override
    public void serviceRemoveRequested(ServiceController controller) { }

    @Override
    public void serviceRemoveRequestCleared(ServiceController controller) { }

    @Override
    public void dependencyFailed(ServiceController controller) { }

    @Override
    public void dependencyFailureCleared(ServiceController controller) { }

    @Override
    public void immediateDependencyUnavailable(ServiceController controller) { }

    @Override
    public void immediateDependencyAvailable(ServiceController controller) { }

    @Override
    public void transitiveDependencyUnavailable(ServiceController controller) { }

    @Override
    public void transitiveDependencyAvailable(ServiceController controller) { }

    private ClojureRuntime runtime;
    private Object callback;
}
