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

package org.immutant.core;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.immutant.bootstrap.ApplicationBootstrapUtils;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.projectodd.polyglot.core.app.ApplicationMetaData;

public class ClojureMetaData extends ApplicationMetaData {

    public static final AttachmentKey<ClojureMetaData> ATTACHMENT_KEY = AttachmentKey.create( ClojureMetaData.class );
    public static final AttachmentKey<File> DESCRIPTOR_FILE = AttachmentKey.create( File.class );
    public static final AttachmentKey<String> FULL_APP_CONFIG = AttachmentKey.create( String.class );
    public static final AttachmentKey<String> LEIN_PROJECT = AttachmentKey.create( String.class );

    public ClojureMetaData(String applicationName, Map<String, Object> config) {
        super( applicationName ); 
        setConfig( config );
        String root = getString( "root" );
        if (root != null) {
            setRoot( new File( root ) );
        }
    }

    @Override
    public void attachTo(DeploymentUnit unit) {
        super.attachTo( unit );
        unit.putAttachment( ATTACHMENT_KEY, this );
    }

    public String getInitFunction() {
        return getString( "init" );
    }

    @SuppressWarnings("rawtypes")
    public List getLeinProfiles() {
        return getList( "lein-profiles" );
    }
    
    /**
     * See if the user has explicitly set :resolve-dependencies. If not, 
     * resolve by default.
     */
    public boolean resolveDependencies() {
        if (this.config.containsKey( "resolve-dependencies" )) {
            return (Boolean)get( "resolve-dependencies" );
        } else {
            return true;
        }
    }
    
    public String getString(String key) {
        return (String)get( key );
    }

    @SuppressWarnings("unchecked")
    public Map<String, ?> getHash(String key) {
        return (Map<String, Object>)get( key );
    }

    @SuppressWarnings("rawtypes")
    public List getList(String key) {
        return (List) get( key );
    }

    public Object get(String key) {
        return this.config.get( key );
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setConfig(Map config) {
        this.config = config;
    }

    public Map<String, Object> getConfig() {
        return this.config;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(File file) throws Exception {
        return ApplicationBootstrapUtils.parseDescriptor( file );
    }

    private Map<String, Object> config;
}
