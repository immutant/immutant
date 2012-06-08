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

import java.io.File;
import java.net.URL;
import java.util.HashMap;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public class JarMountMap extends HashMap<String, String> implements Service<JarMountMap>  {
    public static final AttachmentKey<JarMountMap> ATTACHMENT_KEY = AttachmentKey.create( JarMountMap.class );
    
    @Override
    public JarMountMap getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void stop(StopContext context) {
        // TODO Auto-generated method stub
        
    }

    private static final long serialVersionUID = 1L;
}
