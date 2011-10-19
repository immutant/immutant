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

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.jboss.as.server.deployment.AttachmentKey;

/**
 * Wraps the clojure runtime (clojure.lang.RT) in reflection so we can load different
 * copies under different classloaders, giving us app runtime isolation.
 * 
 * @author Toby Crawley
 *
 */
public class ClojureRuntime {
    public static final AttachmentKey<ClojureRuntime> ATTACHMENT_KEY = AttachmentKey.create( ClojureRuntime.class );
    
    public ClojureRuntime(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
   
    public Object invoke(String namespacedFunction, Object... args) {
        load( CLOJURE_UTIL_NAME ); //TODO: see the performance impact of loading every call. Maybe cache these in production?
        Object func = var( CLOJURE_UTIL_NS, "require-and-invoke" );
        
        return invoke( func, namespacedFunction, args );    
    }
    
    protected Object invoke(Object func, Object... args) {
        return call( func, "invoke", args );
    }
    
    protected void load(String scriptBase) {
        callStatic( getRuntime(), "load", scriptBase );
    }
    
    protected Object var(String namespace, String varName) {
        return callStatic( getRuntime(), "var", namespace, varName );   
    }
    
    protected Object callStatic(Class klass, String methodName, Object... args) {
        return call( klass, null, methodName, args );
    }
    
    protected Object call(Object obj, String methodName, Object... args) {
        return call( obj.getClass(), obj, methodName, args );
    }
        
    protected Object call(Class klass, Object obj, String methodName, Object... args) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        if (klass == null) {
            throw new IllegalArgumentException( "You must provide a class" );
        }
        try {
            Thread.currentThread().setContextClassLoader( this.classLoader );
            ArrayList<Class> paramTypes = new ArrayList<Class>( args.length );
            for(Object each : args) {
                paramTypes.add( each==null ? Object.class : each.getClass() );
            }
            Method method;
            try {
                method = klass.getMethod( methodName, paramTypes.toArray( new Class[0] ) );
            } catch (NoSuchMethodException e) {
                //try again with generic args
                paramTypes.clear();
                for(int i = 0; i < args.length; i++) {
                    paramTypes.add( Object.class );
                }
                method = klass.getMethod( methodName, paramTypes.toArray( new Class[0] ) );
            }

            return method.invoke( obj, args );
        } catch (Exception e) {
            throw new RuntimeException( "Failed to call " + methodName, e );
        } finally {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }
    }
    
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }
    
    protected Class getRuntime() {
        if (this.runtime == null) {
            try {
                this.runtime = this.classLoader.loadClass( RUNTIME_CLASS );
            } catch (ClassNotFoundException e) {
                throw new RuntimeException( "Failed to load " + RUNTIME_CLASS, e );
            }
        }
        
        return this.runtime;
    }
    
    private ClassLoader classLoader;
    private Class runtime;
    
    protected static final String RUNTIME_CLASS = "clojure.lang.RT";   
    protected static final String CLOJURE_UTIL_NAME = "immutant/runtime";
    protected static final String CLOJURE_UTIL_NS = "immutant.runtime";
}
