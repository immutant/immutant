package org.torquebox.clojure.core;

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
   
    public void load(String scriptBase) {
        callStatic( getRuntime(), "load", scriptBase );
    }
   
    public Object invoke(String namespace, String function, Object... args) {
        Object func = var( namespace, function );
        
        return invoke( func, args );
    }
    
    public Object invoke(Object func, Object... args) {
        return call( func, "invoke", args );
    }
    
    public Object var(String namespace, String varName) {
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
                paramTypes.add( each.getClass() );
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
            throw new RuntimeException( "Failed to invoke " + methodName, e );
        } finally {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }
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
}
