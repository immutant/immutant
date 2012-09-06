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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.module.ResourceRoot;

public class TmpResourceMountMap {
    public static final AttachmentKey<TmpResourceMountMap> ATTACHMENT_KEY = AttachmentKey.create( TmpResourceMountMap.class );
    
    public void put(URL mountURL, ResourceRoot resourceRoot, File actualFile, boolean unmountable) {
        map.put( mountURL.toExternalForm(), new Entry( resourceRoot, actualFile, unmountable ) );
    }
    
    
    public ResourceRoot getResourceRoot(URL mountURL) {
        return getResourceRoot( mountURL.toExternalForm() );
    }
    
    public ResourceRoot getResourceRoot(String mountPath) {
        Entry entry = map.get( mountPath );
        if (entry != null) {
            return entry.resourceRoot;
        } else {
            return null;
        }
    }
    
    public boolean isUnmountable(String mountPath) {
        Entry entry = map.get( mountPath );
        if (entry != null) {
            return entry.unmountable;
        } else {
            return false;
        }
    }
    
    public File getActualFile(URL mountURL) {
        return getActualFile( mountURL.toExternalForm() );
    }
    
    public File getActualFile(String mountPath) {
        Entry entry = map.get( mountPath );
        if (entry != null) {
            return entry.actualFile;
        } else {
            return null;
        }
    }
    
    public void remove(URL mountURL) {
        remove( mountURL.toExternalForm() );
    }
    
    public void remove(String mountPath) {
        map.remove( mountPath );
    }
    
    private Map<String, Entry> map = new HashMap<String, Entry>();
    
    class Entry {
        public ResourceRoot resourceRoot;
        public File actualFile;
        public boolean unmountable;
        
        Entry(ResourceRoot resourceRoot, File actualFile, boolean unmountable) {
            this.resourceRoot = resourceRoot;
            this.actualFile = actualFile;
            this.unmountable = unmountable;
        }
    }
}
