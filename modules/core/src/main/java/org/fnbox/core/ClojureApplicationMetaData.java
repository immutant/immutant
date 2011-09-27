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

package org.fnbox.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

import clojure.lang.Compiler;
import clojure.lang.Keyword;
import clojure.lang.Var;

public class ClojureApplicationMetaData {

    public static final AttachmentKey<ClojureApplicationMetaData> ATTACHMENT_KEY = AttachmentKey.create( ClojureApplicationMetaData.class );

    public static final String DEFAULT_ENVIRONMENT_NAME = "development";



    public ClojureApplicationMetaData(String applicationName, Map<String, ?> config) {
        this.applicationName = sanitize( applicationName );
        this.config = config;
    }

    private String sanitize(String name) {
        int lastSlash = name.lastIndexOf( "/" );
        if ( lastSlash >= 0 ) {
            name = name.substring( lastSlash+1 );
        }
        int lastDot = name.lastIndexOf( "." );
        if (lastDot >= 0) {
            name = name.substring( 0, lastDot );
        }
        int lastKnob = name.lastIndexOf( "-knob" );
        if (lastKnob >= 0) {
            name = name.substring( 0, lastKnob );
        }
        return name.replaceAll( "\\.", "-" );
    }

    public void applyDefaults() {
        if (this.environmentName == null) {
            this.environmentName = DEFAULT_ENVIRONMENT_NAME;
        }
    }

    public VirtualFile getApplicationRootFile() {
        String path = getRootPath();

        if (path == null) {
            return null;
        }
        
        String sanitizedPath = null;

        if (path.indexOf( "\\\\" ) >= 0) {
            sanitizedPath = path.replaceAll( "\\\\\\\\", "/" );
            sanitizedPath = sanitizedPath.replaceAll( "\\\\", "" );
        } else {
            sanitizedPath = path.replaceAll( "\\\\", "/" );
        }
        
        if ( sanitizedPath.startsWith( "~" ) ) {
            sanitizedPath = System.getProperty( "user.home" ) + sanitizedPath.substring( 1 );
        }
        VirtualFile root = VFS.getChild( sanitizedPath );

        return root;
    }
    
    public void setRoot(VirtualFile root) {
        this.root = root;
    }

    public void setRoot(String path) {
        if (path != null) {
            String sanitizedPath = null;

            if (path.indexOf( "\\\\" ) >= 0) {
                sanitizedPath = path.replaceAll( "\\\\\\\\", "/" );
                sanitizedPath = sanitizedPath.replaceAll( "\\\\", "" );
            } else {
                sanitizedPath = path.replaceAll( "\\\\", "/" );
            }
            VirtualFile root = VFS.getChild( sanitizedPath );
            setRoot( root );
        }
    }

    public VirtualFile getRoot() {
        return this.root;
    }

    public String getRootPath() {
        return getString( "root" );
        //        try {
        //            return getRoot().toURL().toString();
        //        } catch (Exception e) {
        //            return "";
        //        }
    }

    public String getAppFunction() {
        return getString( "app-function" );
    }

    public String getApplicationName() {
        return this.applicationName;
    }

    public boolean isArchive() {
        return this.archive;
    }

    public boolean isDevelopmentMode() {
        String env = this.environmentName;
        return env == null || env.trim().equalsIgnoreCase( "development" );
    }

    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }

    public String getEnvironmentName() {
        return this.environmentName;
    }

    public void setEnvironmentVariables(Map<String, String> environment) {
        this.environment = environment;
    }

    public Map<String, String> getEnvironmentVariables() {
        return this.environment;
    }

    public String toString() {
        return "[ClojureApplicationMetaData:\n  root=" + this.root + "\n  environmentName=" + this.environmentName + "\n  archive=" + this.archive + "\n  environment="
                + this.environment + "]";
    }

    public String getString(String key) {
        return (String)this.config.get( key );
    }

    public static Map<String, ?> parse(File file) throws Exception {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader( Var.class.getClassLoader() );
            Map<String, Object> config = new HashMap<String, Object>();
            Map<Keyword, Object> cljConfig = (Map<Keyword, Object>)Compiler.loadFile( file.getAbsolutePath() ); 
            for( Keyword each : cljConfig.keySet()) {
                config.put( each.getName(), cljConfig.get( each ) );
            }
            
            return config; 
        } finally {
            Thread.currentThread().setContextClassLoader( originalCl );
        }
        
    }

    private Map<String, ?> config;
    private VirtualFile root;
    private String applicationName;
    private String environmentName;
    private Map<String, String> environment;
    private boolean archive = false;
}
