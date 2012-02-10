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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.projectodd.polyglot.core.app.ApplicationMetaData;

public class ClojureMetaData extends ApplicationMetaData {

    public static final AttachmentKey<ClojureMetaData> ATTACHMENT_KEY = AttachmentKey.create( ClojureMetaData.class );

    public ClojureMetaData(String applicationName, Map<String, ?> config) {
        super( applicationName ); 
        this.config = config;
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
    
    public String getString(String key) {
        return (String)this.config.get( key );
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, ?> getHash(String key) {
        return (Map<String, Object>)this.config.get( key );
    }
    
    @SuppressWarnings("rawtypes")
    public List getList(String key) {
        return (List) this.config.get( key );
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setLeinProject(Map leinProject) {
        this.leinProject = leinProject;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ?> parse(File file) throws Exception {
        return ApplicationBootstrapUtils.parseDescriptor( file );
    }

    private Map<String, ?> config;
    private Map<String, ?> leinProject;
}
