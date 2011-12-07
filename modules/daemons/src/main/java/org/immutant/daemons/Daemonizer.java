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

import java.util.*;
import javax.management.MBeanServer;
import org.immutant.daemons.as.DaemonServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.jmx.*;
import org.jboss.msc.service.*;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.logging.Logger;
import org.projectodd.polyglot.core.app.ApplicationMetaData;
// import org.projectodd.polyglot.hasingleton.HASingleton;


public class Daemonizer implements Service<Daemonizer> {

    public Daemonizer(DeploymentUnit unit) {
        this.unit = unit;
    }

    public void deploy(final String daemonName, Runnable start, Runnable stop, boolean singleton) {

        Daemon daemon = new Daemon(start, stop);
        DaemonService daemonService = new DaemonService( daemon );
        ServiceName serviceName = DaemonServices.daemon( unit, daemonName );
        ServiceBuilder<Daemon> builder = this.serviceTarget.addService(serviceName, daemonService);
        // if (singleton) {
        //     builder.addDependency(HASingleton.serviceName(unit));
        // }
        builder.setInitialMode(Mode.PASSIVE);
        builder.install();

        installMBean(serviceName, daemon);
    }

    public void start(StartContext context) throws StartException {
        this.serviceTarget = context.getChildTarget();
    }

    public synchronized void stop(StopContext context) {
    }

    public Daemonizer getValue() {
        return this;
    }

    protected void installMBean(final ServiceName name, Daemon daemon) {
        final ApplicationMetaData appMetaData = unit.getAttachment( ApplicationMetaData.ATTACHMENT_KEY );
        String mbeanName = ObjectNameFactory.create( "immutant.daemons", new Hashtable<String, String>() {
                {
                    put( "app", appMetaData.getApplicationName() );
                    put( "name", name.getSimpleName() );
                }
            } ).toString();

        MBeanRegistrationService<DaemonMBean> mbeanService = new MBeanRegistrationService<DaemonMBean>( mbeanName, new ImmediateValue<DaemonMBean>( daemon ) );
        this.serviceTarget.addService( name.append( "mbean" ), mbeanService )
            .addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() )
            .setInitialMode( Mode.PASSIVE )
            .install(); 
    }

    private DeploymentUnit unit;;
    private ServiceTarget serviceTarget;
    private static final Logger log = Logger.getLogger( Daemonizer.class );
}
