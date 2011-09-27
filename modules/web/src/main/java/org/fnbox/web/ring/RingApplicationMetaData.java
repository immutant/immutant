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

package org.fnbox.web.ring;

import java.util.ArrayList;
import java.util.List;

import org.fnbox.core.ClojureApplicationMetaData;
import org.jboss.as.server.deployment.AttachmentKey;

public class RingApplicationMetaData {

    public static final AttachmentKey<RingApplicationMetaData> ATTACHMENT_KEY = AttachmentKey.create(RingApplicationMetaData.class);
    
    public RingApplicationMetaData(ClojureApplicationMetaData appMetaData) {
        this.appMetaData = appMetaData;
    }

    public void addHost(String host) {
        if (host != null && !this.hosts.contains( host ))
            this.hosts.add( host );
    }

    public List<String> getHosts() {
        return this.hosts;
    }

    public String getStaticPathPrefix() {
        String prefix = this.appMetaData.getString( "static" );
        if (prefix == null) {
            prefix = "public";
        }
        
        return prefix;
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

    
    private ClojureApplicationMetaData appMetaData;
    private List<String> hosts = new ArrayList<String>();
    private String contextPath = "/";
}
