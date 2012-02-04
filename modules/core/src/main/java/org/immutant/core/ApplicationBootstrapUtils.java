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
import java.util.Map;

import clojure.lang.RT;
import clojure.lang.Var;

public class ApplicationBootstrapUtils {

    public static Map<String, Object> parseDescriptor(File file) throws Exception {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader( Var.class.getClassLoader() );
            RT.load( "immutant/bootstrap" );
            Var reader = RT.var( "immutant.bootstrap", "read-descriptor" );
            return (Map<String, Object>) reader.invoke( file );
        } finally {
            Thread.currentThread().setContextClassLoader( originalCl );
        }
    }
}
