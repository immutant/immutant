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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
        initializeThreadPool();
    }
   
    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public synchronized void stop(StopContext context) {
        log.info( "Shutting down Clojure runtime for " + this.name );
        invoke( "clojure.core/shutdown-agents" );
        this.threadPool.setKeepAliveTime( 1, TimeUnit.MILLISECONDS );
        this.threadPool.shutdownNow();
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
    
    protected void initializeThreadPool() {
        this.threadPool = (ThreadPoolExecutor)Executors.newCachedThreadPool(  
                new ThreadFactory() {   
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable);
                        thread.setName(ClojureRuntime.this.name + 
                                " runtime-thread-" + ClojureRuntime.this.threadCounter.getAndIncrement() );
                        thread.setContextClassLoader( ClojureRuntime.this.classLoader );
                        return thread;
                    }
                });
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
        try {
            ArrayList<Class> paramTypes = new ArrayList<Class>( args.length );
            for(Object each : args) {
                paramTypes.add( each==null ? Object.class : each.getClass() );
            }
            
            Method meth;
            try {
                meth = klass.getMethod( methodName, paramTypes.toArray( new Class[0] ) );
            } catch (NoSuchMethodException e) {
                //try again with generic args
                paramTypes.clear();
                for(int i = 0; i < args.length; i++) {
                    paramTypes.add( Object.class );
                }
                meth = klass.getMethod( methodName, paramTypes.toArray( new Class[0] ) );
            }
            
            final Method method = meth;
           
            // execute clojure calls in a thread pool to prevent its ThreadLocals from 
            // retaining memory on undeploy since the MSC recycles threads
            return this.threadPool.submit( new Callable() {
                public Object call() {
                    try {
                        return method.invoke( obj, args );
                    } catch (Exception e) {
                        throw new RuntimeException( "Failed to invoke " + methodName, e );
                    }
                }
            }).get();

        } catch (Exception e) {
            throw new RuntimeException( "Failed to call " + methodName, e );
        }

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
    private ThreadPoolExecutor threadPool;
    private AtomicLong threadCounter = new AtomicLong( 1 );
    
    protected static final String RUNTIME_CLASS = "clojure.lang.RT";   
    protected static final String CLOJURE_UTIL_NAME = "immutant/runtime";
    protected static final String CLOJURE_UTIL_NS = "immutant.runtime";
    
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
