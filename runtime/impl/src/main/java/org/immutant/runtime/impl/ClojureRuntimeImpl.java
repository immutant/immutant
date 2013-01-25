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

package org.immutant.runtime.impl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.immutant.runtime.ClojureRuntime;
import org.jboss.logging.Logger;

import clojure.lang.Agent;
import clojure.lang.IFn;
import clojure.lang.LockingTransaction;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class ClojureRuntimeImpl extends ClojureRuntime {

    public void init() {
        StackData stackData = preInvoke();
        try {
            Var require = RT.var( "clojure.core", "require" );
            require.invoke( Symbol.create( "immutant.registry" ) );
            require.invoke( Symbol.create( "immutant.runtime" ) );
        } finally {
            postInvoke(stackData);
        }
    }

    protected StackData preInvoke() {
        ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader(); 
        Thread.currentThread().setContextClassLoader( this.classLoader );
        Boolean inStack = stackEntered.get();
        if (inStack == null) { 
            stackEntered.set( true );
        }
        
        return new StackData( originalClassloader, inStack );
    }

    protected void postInvoke(StackData stackData) {
        if (stackData.entryPoint) {
            try {
                // leaving these thread locals around will cause memory leaks when the application
                // is undeployed.
                removeThreadLocal( LockingTransaction.class, "transaction" );
                removeThreadLocal( Var.class, "dvals" );

                // this one doesn't leak, but leaving it set can theoretically allow another
                // application to read the value off of the thread when it is doled out
                //again from the pool
                removeThreadLocal( Agent.class, "nested" );
            } catch (Exception e) {
                log.error( "Failed to clear thread locals: ", e );
            } finally {
                this.stackEntered.remove();
                Thread.currentThread().setContextClassLoader( stackData.loader );
            }
        }
    }

    protected Var var(String namespacedFunction) {
        Var var = this.varCache.get( namespacedFunction );
        if (var == null) {
            StackData stackData = preInvoke();
            try {
                String[] parts = namespacedFunction.split( "/" );
                RT.var( "clojure.core", "require" ).invoke( Symbol.create( parts[0] ) );
                var = RT.var( parts[0], parts[1] );
                this.varCache.put( namespacedFunction, var );
            } catch (Exception e) {
                throw new RuntimeException( "Failed to load Var " + namespacedFunction, e );
            } finally {
                postInvoke( stackData );
            }
        }

        return var;
    }

    @SuppressWarnings("rawtypes")
    protected void removeThreadLocal( Class klass, String fieldName ) throws Exception {
        Field field = lookupField( klass, fieldName );
        if (field != null) {
            ThreadLocal tl = (ThreadLocal)field.get( null );
            if (tl != null) {
                tl.remove();
            }
        }
    }

    @SuppressWarnings("rawtypes")
    protected Field lookupField(Class klass, String fieldName) throws NoSuchFieldException {
        Map<String, Field> fields = this.fieldCache.get( klass );
        if (fields == null) {
            fields = new HashMap<String, Field>();
            this.fieldCache.put( klass, fields );
        }

        Field field = fields.get( fieldName );
        if (field == null) {
            field = klass.getDeclaredField( fieldName );
            field.setAccessible( true );
            fields.put( fieldName, field );
        }

        return field;
    }

    @Override
    public Object invoke(String namespacedFunction) {
        return invoke( var( namespacedFunction ) );
    }

    @Override
    public Object invoke(Object fn) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke();
        } finally {
            postInvoke( stackData );
        }
    }
    
    @Override
    public Object invoke(String namespacedFunction, Object arg1) {
        return invoke( var( namespacedFunction ), arg1 );
    }

    @Override
    public Object invoke(Object fn, Object arg1) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1);
        } finally {
            postInvoke( stackData );
        }
    }
    
    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2) {
        return invoke( var( namespacedFunction ), arg1, arg2);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3) {
        return invoke( var( namespacedFunction ), arg1, arg2, arg3);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2, arg3);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16, arg17);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16, arg17);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19, Object arg20) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19, arg20);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19, Object arg20) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19, arg20);
        } finally {
            postInvoke( stackData );
        }
    }

    @Override
    public Object invoke(String namespacedFunction, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19, Object arg20,
            Object... args) {
        return invoke(var( namespacedFunction ), arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19, arg20,
                    args);
    }

    @Override
    public Object invoke(Object fn, Object arg1, Object arg2,
            Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            Object arg8, Object arg9, Object arg10, Object arg11, Object arg12,
            Object arg13, Object arg14, Object arg15, Object arg16,
            Object arg17, Object arg18, Object arg19, Object arg20,
            Object... args) {
        StackData stackData = preInvoke();
        try {
            return ((IFn)fn).invoke(arg1, arg2,
                    arg3, arg4, arg5, arg6, arg7,
                    arg8, arg9, arg10, arg11, arg12,
                    arg13, arg14, arg15, arg16,
                    arg17, arg18, arg19, arg20,
                    args);
        } finally {
            postInvoke( stackData );
        }
    }

    class StackData {
        public ClassLoader loader;
        public boolean entryPoint;
        
        public StackData(ClassLoader loader, Boolean alreadyInStack) {
            this.loader = loader;
            this.entryPoint = (alreadyInStack == null);
        }
    }
    
    private ThreadLocal<Boolean> stackEntered = new ThreadLocal<Boolean>();
   
    private Map<String, Var> varCache = new HashMap<String, Var>();
    @SuppressWarnings("rawtypes")
    private HashMap<Class, Map<String, Field>> fieldCache = new HashMap<Class, Map<String, Field>>();

    static final Logger log = Logger.getLogger( "org.immutant.runtime" );


}
