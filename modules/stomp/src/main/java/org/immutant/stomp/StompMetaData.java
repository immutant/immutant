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

package org.immutant.stomp;

import java.util.ArrayList;
import java.util.List;

import org.immutant.core.ClojureMetaData;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.projectodd.polyglot.stomp.StompApplicationMetaData;

public class StompMetaData extends StompApplicationMetaData {

    public static final AttachmentKey<StompMetaData> ATTACHMENT_KEY = AttachmentKey.create(StompMetaData.class);
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public StompMetaData(ClojureMetaData appMetaData) {
        this.appMetaData = appMetaData;
        final Object host = this.appMetaData.get( "virtual-host" );
        if (host != null) {
            if (host instanceof List) {
                addHosts( (List)host );
            } else {
                addHost( (String)host );
            }
        }
        
        setContextPath( appMetaData.getString( "context-path" ) );
    }

    @Override
    public void attachTo(DeploymentUnit unit) {
        super.attachTo( unit );
        unit.putAttachment( ATTACHMENT_KEY, this );
    }
    
    
    public String toString() {
        return "[StompMetaData:" + System.identityHashCode( this ) + "\n  host=" + getHosts() + "\n  context=" + getContextPath() + "]";
    }

    
    private ClojureMetaData appMetaData;
}
