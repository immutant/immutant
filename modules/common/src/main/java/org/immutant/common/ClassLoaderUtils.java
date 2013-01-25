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

package org.immutant.common;

import java.util.concurrent.Callable;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.modules.Module;

public class ClassLoaderUtils {

    @SuppressWarnings("rawtypes")
    public static void init(ClassLoaderFactory aLoaderFactory, AttachmentKey aMountMapKey) {
        loaderFactory = aLoaderFactory;
        mountMapKey = aMountMapKey;
    }
    
    @SuppressWarnings("unchecked")
    public static ClassLoader getModuleLoader(DeploymentUnit unit) {
        Module module = unit.getAttachment( Attachments.MODULE );
        
        if (module != null) {
            return loaderFactory.newInstance( module.getClassLoader(),
                    unit.getAttachment( mountMapKey ) );
        } else {
            // this won't happen in production, but helps testing    
            return ClassLoaderUtils.class.getClassLoader(); 
        }
    }
    
    @SuppressWarnings("rawtypes")
    public static Object callInLoader(Callable body, ClassLoader loader) throws Exception {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader( loader );
            return body.call();
        } finally {
            Thread.currentThread().setContextClassLoader( originalCl );
        }
    }
    
    private static ClassLoaderFactory loaderFactory;
    @SuppressWarnings("rawtypes")
    private static AttachmentKey mountMapKey;
}
