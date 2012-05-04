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
package org.immutant.runtime.impl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.immutant.runtime.ClojureRuntime;
import org.jboss.logging.Logger;

import clojure.lang.Agent;
import clojure.lang.ArraySeq;
import clojure.lang.LockingTransaction;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class ClojureRuntimeImpl extends ClojureRuntime {

    @Override
    public Object invoke(String namespacedFunction, Object... args) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).applyTo( ArraySeq.create( args ) );
        } finally {
            postInvoke( originalClassloader );
        }
    }
    
    protected ClassLoader preInvoke() {
        ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader(); 
        Thread.currentThread().setContextClassLoader( this.classLoader );
       
        return originalClassloader;
    }
    
    protected void postInvoke(ClassLoader originalClassloader) {
        try {
            // leaving these thread locals around will cause memory leaks when the application
            // is undeployed.
            removeThreadLocal( Var.class, "dvals" );
            removeThreadLocal( Agent.class, "nested" );
            removeThreadLocal( LockingTransaction.class, "transaction" );
        } catch (Exception e) {
            log.error( "Failed to clear thread locals: ", e );
        } finally {
            Thread.currentThread().setContextClassLoader( originalClassloader );
        }
    }

    protected Var var(String namespacedFunction) {
        Var var = this.varCache.get( namespacedFunction );
        if (var == null) {
            try {
                String[] parts = namespacedFunction.split( "/" );

                if (!loadedNamespaces.contains( parts[0] )) {
                    if (this.requireFunction == null) {
                        this.requireFunction = RT.var( "clojure.core", "require" );
                    }

                    this.requireFunction.invoke( Symbol.create( parts[0] ) );
                    loadedNamespaces.add( parts[0] );
                }

                var = RT.var( parts[0], parts[1] );
                this.varCache.put( namespacedFunction, var );
            } catch (Exception e) {
                throw new RuntimeException( "Failed to load Var " + namespacedFunction, e );
            }
        }

        return var;
    }

    @SuppressWarnings("rawtypes")
    protected void removeThreadLocal( Class klass, String fieldName ) throws Exception {
        Field field = lookupField( klass, fieldName, true );
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

    private Var requireFunction;
    private Set<String> loadedNamespaces = new HashSet<String>(); 
    private Map<String, Var> varCache = new HashMap<String, Var>();
    @SuppressWarnings("rawtypes")
    private HashMap<Class, Map<String, Field>> fieldCache = new HashMap<Class, Map<String, Field>>();

    static final Logger log = Logger.getLogger( "org.immutant.runtime" );
}
