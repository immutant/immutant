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

package org.immutant.web.ring;

import java.util.ArrayList;
import java.util.List;

import org.immutant.core.ClojureMetaData;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.projectodd.polyglot.web.WebApplicationMetaData;

public class RingMetaData extends WebApplicationMetaData {

    public static final AttachmentKey<RingMetaData> ATTACHMENT_KEY = AttachmentKey.create(RingMetaData.class);
    
    public RingMetaData(ClojureMetaData appMetaData) {
        this.appMetaData = appMetaData;
        setStaticPathPrefix( this.appMetaData.getString( "static" ) );
    }

    @Override
    public void attachTo(DeploymentUnit unit) {
        super.attachTo( unit );
        unit.putAttachment( ATTACHMENT_KEY, this );
    }
    
    public void setContextPath(String contextPath) {
        if (contextPath != null) this.contextPath = contextPath;
    }

    public String getContextPath() {
        return this.appMetaData.getString( "context-path" );
    }

    public String toString() {
        return "[RingApplicationMetaData:" + System.identityHashCode( this ) + "\n  host=" + this.hosts + "\n  context=" + this.contextPath + "]";
    }

    
    private ClojureMetaData appMetaData;
    private List<String> hosts = new ArrayList<String>();
    private String contextPath = "/";
}
