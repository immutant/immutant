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
import java.util.Map;

import org.immutant.runtime.ClojureRuntime;
import org.jboss.logging.Logger;

import clojure.lang.Agent;
import clojure.lang.ArraySeq;
import clojure.lang.LockingTransaction;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class ClojureRuntimeImpl extends ClojureRuntime {

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
                RT.var( "clojure.core", "require" ).invoke( Symbol.create( parts[0] ) );
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

    @Override
    public Object invoke(String namespacedFunction) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke();
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16, arg17);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19, Object arg20) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19, arg20);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19, Object arg20,
            Object... args) {
        ClassLoader originalClassloader = preInvoke();
        try {
            return var( namespacedFunction ).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19, arg20,
                    args);
        } finally {
            postInvoke( originalClassloader );
        }
    }

    private Map<String, Var> varCache = new HashMap<String, Var>();
    @SuppressWarnings("rawtypes")
    private HashMap<Class, Map<String, Field>> fieldCache = new HashMap<Class, Map<String, Field>>();

    static final Logger log = Logger.getLogger( "org.immutant.runtime" );


}
