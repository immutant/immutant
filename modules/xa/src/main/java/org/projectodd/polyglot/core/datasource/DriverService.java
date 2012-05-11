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

package org.projectodd.polyglot.core.datasource;

import java.sql.Driver;

import org.jboss.as.connector.registry.DriverRegistry;
import org.jboss.as.connector.registry.InstalledDriver;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.datasource.db.Adapter;


public class DriverService implements Service<Driver> {

    public DriverService(Adapter adapter, ClassLoader classloader) {
        this.adapter = adapter;
        this.classloader = classloader;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            DriverService.this.driver = instantiateDriver();
            log.debug( "driver: " + DriverService.this.driver );
            DriverService.this.installedDriver = createInstalledDriver();
            
            DriverRegistry registry = DriverService.this.driverRegistryInjector.getValue();
            registry.registerInstalledDriver( installedDriver );
            
        } catch (Exception e) {
            throw new StartException( e );
        }
    }

    @Override
    public void stop(StopContext context) {
        this.driverRegistryInjector.getValue().unregisterInstalledDriver( this.installedDriver );
    }

    protected Driver instantiateDriver() throws Exception {
        final Class<? extends Driver> driverClass = classloader.loadClass( this.adapter.getDriverClassName() ).asSubclass( Driver.class );
        return driverClass.newInstance();
    }

    protected InstalledDriver createInstalledDriver() {
        int major = this.driver.getMajorVersion();
        int minor = this.driver.getMinorVersion();
        boolean compliant = this.driver.jdbcCompliant();
        return new InstalledDriver(this.adapter.getId(), this.driver.getClass().getName(), null, null, major, minor, compliant );
    }

    @Override
    public Driver getValue() throws IllegalStateException, IllegalArgumentException {
        return this.driver;
    }

    public Injector<DriverRegistry> getDriverRegistryInjector() {
        return this.driverRegistryInjector;
    }


    private static final Logger log = Logger.getLogger( DriverService.class );

    private InjectedValue<DriverRegistry> driverRegistryInjector = new InjectedValue<DriverRegistry>();
    private Adapter adapter;
    private Driver driver;
    private InstalledDriver installedDriver;
    private ClassLoader classloader;
}
