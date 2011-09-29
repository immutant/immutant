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
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.projectodd.polyglot.core.app.ApplicationMetaData;

import clojure.lang.Compiler;
import clojure.lang.Keyword;
import clojure.lang.Var;

public class ClojureMetaData extends ApplicationMetaData {

    public static final AttachmentKey<ClojureMetaData> ATTACHMENT_KEY = AttachmentKey.create( ClojureMetaData.class );

    public ClojureMetaData(String applicationName, Map<String, ?> config) {
        super( applicationName ); 
        this.config = config;
        setRoot( getString( "root" ) );
    }

    @Override
    public void attachTo(DeploymentUnit unit) {
        super.attachTo( unit );
        unit.putAttachment( ATTACHMENT_KEY, this );
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

    public String getAppFunction() {
        return getString( "app-function" );
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
}
