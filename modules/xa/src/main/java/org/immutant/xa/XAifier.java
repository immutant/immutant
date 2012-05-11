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

package org.immutant.xa;

import java.util.Map;

import org.immutant.xa.as.XaServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;
import org.projectodd.polyglot.core.datasource.DataSourceFactory;
import org.projectodd.polyglot.core_extensions.AtRuntimeInstaller;


public class XAifier extends AtRuntimeInstaller<XAifier> {

    public XAifier(DeploymentUnit unit) {
        super( unit );
    }
    
    public String createDataSource(final String name, Map<String,Object> spec) {
        String jndiName = getFactory().create(name, spec);
        synchronized (jndiName) {
            try { jndiName.wait(10000); } catch (InterruptedException ignored) {}
        }
        return jndiName;
    }
    
    protected synchronized DataSourceFactory getFactory() {
        if (null == factory) {
            factory = new DataSourceFactory(getUnit(), getTarget());
        }
        return factory;
    }

    private DataSourceFactory factory;
        
}
