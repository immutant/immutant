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

package org.immutant.stomp;

import org.immutant.runtime.ClojureRuntime;
import org.immutant.runtime.ClojureRuntimeService;
import org.immutant.stomp.as.StompServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.core_extensions.AtRuntimeInstaller;
import org.projectodd.stilts.stomplet.container.SimpleStompletContainer;

public class Stompletizer extends AtRuntimeInstaller<Stompletizer> {

    public Stompletizer(DeploymentUnit unit) {
        super( unit );
    }

    public ClojureStomplet createStomplet(final String route, Object handler) {
        ClojureRuntime runtime = getUnit().getAttachment( ClojureRuntimeService.ATTACHMENT_KEY );
        
        final ServiceName serviceName = StompServices.stomplet( getUnit(), route );
        final ClojureStomplet stomplet = new ClojureStomplet( runtime, route, handler );
        
        replaceService( serviceName, new Runnable() {
            public void run() {
                build( serviceName, stomplet, false )
                .addDependency( StompServices.container( getUnit() ), SimpleStompletContainer.class, stomplet.getStompletContainerInjector() )
                .install();
            }
        });
        
        return stomplet;
    }
    
}
