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
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Turns vfs: urls into file: urls for files that exist outside of an archive.
 * 
 * @author tcrawley@redhat.com
 *
 */
public class VFSStrippingClassLoader extends ClassLoader {

    public VFSStrippingClassLoader(ClassLoader parent, JarMountMap mountMap) {
        super( parent );
        this.mountMap = mountMap;
    }
    
    @Override
    public URL getResource(String name) {
        URL url = getParent().getResource( name );
        if (url != null && "vfs".equals( url.getProtocol() )) {
            try {   
                String urlAsString = url.toExternalForm();
                int splitPoint = urlAsString.indexOf( ".jar/" ) + 5;
                String jarPrefix = urlAsString.substring( 0, splitPoint );
                String fileName = urlAsString.substring( splitPoint );
                if (this.mountMap.containsKey( jarPrefix )) {
                    //translate resources within tmp mounted jars to the actual path. see IMMUTANT-70
                    url = new URL( "jar:" + this.mountMap.get( jarPrefix ) + "!/" + fileName );
                } else {
                    URL nonVFSUrl = new URL( "file" + url.toExternalForm().substring( 3 ) );
                    if ((new File( nonVFSUrl.toURI() )).exists()) {
                        url = nonVFSUrl;
                    }
                } 
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return url;
    }

    public String toString() {
        return VFSStrippingClassLoader.class.getName() + "[" + getParent().toString() + "]";
    }
    
    private JarMountMap mountMap;
    
}
