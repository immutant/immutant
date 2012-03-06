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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Wraps the clojure runtime (clojure.lang.RT) in reflection so we can load different
 * copies under different classloaders, giving us app runtime isolation.
 * 
 * @author Toby Crawley
 *
 */
public class ClojureRuntime implements Service<ClojureRuntime> {
    public static final AttachmentKey<ClojureRuntime> ATTACHMENT_KEY = AttachmentKey.create( ClojureRuntime.class );
    
    public ClojureRuntime(ClassLoader classLoader, String name) {
        this.classLoader = classLoader;
        this.name = name;
    }
   
    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public synchronized void stop(StopContext context) {
        log.info( "Shutting down Clojure runtime for " + this.name );
        invoke( "clojure.core/shutdown-agents" );
    }
    
    @Override 
    public ClojureRuntime getValue() {
        return this;
    }
    
    public ClassLoader getClassLoader() {
        return this.classLoader;
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
    
    @SuppressWarnings("rawtypes")
    protected Object callStatic(Class klass, String methodName, Object... args) {
        return call( klass, null, methodName, args );
    }
    
    protected Object call(Object obj, String methodName, Object... args) {
        return call( obj.getClass(), obj, methodName, args );
    }
        
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object call(final Class klass, final Object obj, final String methodName, final Object... args) {
        if (klass == null) {
            throw new IllegalArgumentException( "You must provide a class" );
        }
        
        ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
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
            
            Object retval = method.invoke( obj, args );

            // leaving these thread locals around will cause memory leaks when the application
            // is undeployed.
            removeThreadLocal( "clojure.lang.Var", "dvals" );
            removeThreadLocal( "clojure.lang.Agent", "nested" );
            removeThreadLocal( "clojure.lang.LockingTransaction", "transaction" );
            
            return retval;
            
        } catch (Exception e) {
            throw new RuntimeException( "Failed to call " + methodName, e );
        } finally {
            Thread.currentThread().setContextClassLoader( originalClassloader );
        }
    }
    
    @SuppressWarnings("rawtypes")
    protected void removeThreadLocal(String className, String fieldName) throws Exception {
        Field field = lookupField( loadClass( className ), fieldName, true );
        if (field != null) {
            ThreadLocal tl = (ThreadLocal)field.get( null );
            if (tl != null) {
                tl.remove();
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    protected Field lookupField(Class klass, String fieldName, boolean makeAccessible) throws NoSuchFieldException {
        Map<String, Field> fields = this.fieldCache.get( klass );
        if (fields == null) {
            fields = new HashMap<String, Field>();
            this.fieldCache.put( klass, fields );
        }
        
        Field field = fields.get( fieldName );
        if (field == null) {
            field = klass.getDeclaredField( fieldName );
            if (makeAccessible) {
                field.setAccessible( true );
            }
            fields.put( fieldName, field );
        }
        
        return field;
    }
    
    @SuppressWarnings("rawtypes")
    protected Class loadClass(String className) {
        try {
            return this.classLoader.loadClass( className );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException( "Failed to load " + className, e );
        }
    }
    
    @SuppressWarnings("rawtypes")
    protected Class getRuntime() {
        if (this.runtime == null) {
            //initialize();
            this.runtime = loadClass( RUNTIME_CLASS );
        }
        
        return this.runtime;
    }
    
    private ClassLoader classLoader;
    @SuppressWarnings("rawtypes")
    private Class runtime;
    private String name;
    @SuppressWarnings("rawtypes")
    private HashMap<Class, Map<String, Field>> fieldCache = new HashMap<Class, Map<String, Field>>();
    
    protected static final String RUNTIME_CLASS = "clojure.lang.RT";   
    protected static final String CLOJURE_UTIL_NAME = "immutant/runtime";
    protected static final String CLOJURE_UTIL_NS = "immutant.runtime";
    
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
