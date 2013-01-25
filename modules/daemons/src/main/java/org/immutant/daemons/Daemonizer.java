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

package org.immutant.daemons;

import org.immutant.common.ClassLoaderUtils;
import org.immutant.daemons.as.DaemonServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.core_extensions.AtRuntimeInstaller;


public class Daemonizer extends AtRuntimeInstaller<Daemonizer> {

    public Daemonizer(DeploymentUnit unit) {
        super( unit );
    }

    public Daemon createDaemon(final String daemonName, Runnable start, Runnable stop, boolean singleton) {
        ServiceName serviceName = DaemonServices.daemon( getUnit(), daemonName );
        Daemon daemon = new Daemon( ClassLoaderUtils.getModuleLoader( getUnit() ), start, stop );
        
        deploy( serviceName, daemon, singleton );
        installMBean( serviceName, "immutant.daemons", daemon );
        
        return daemon;
    }
    
}
