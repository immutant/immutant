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
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.immutant.common.ClassLoaderFactory;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.projectodd.polyglot.core.util.ResourceLoaderUtil;


/**
 * A ClassLoader wrapper that lets us intercept various parts of the class loading process.
 * Currently, this just turns vfs: urls into file: urls for files that exist outside of an archive and
 * provides a mechanism for accessing all of the paths used by the parent ModuleClassLoader.
 * 
 * @author tcrawley@redhat.com
 *
 */
public class ImmutantClassLoader extends ClassLoader {

    public ImmutantClassLoader(ClassLoader parent, TmpResourceMountMap mountMap) {
        super( parent );
        this.mountMap = mountMap;
    }
    
    @Override
    public URL getResource(String name) {
        URL url = getParent().getResource( name );
        
        return stripVFS( url );
    }

    public List<URL> getResourcePaths() {
        ArrayList<URL> urls = new ArrayList<URL>();
        try {
            ResourceLoader[] loaders = ResourceLoaderUtil.getExistingResourceLoaders( (ModuleClassLoader)getParent() );

            for (ResourceLoader loader : loaders) {
                Resource resource = loader.getResource( "/" );
                if (resource != null) {
                    URL url = resource.getURL();
                    File file = getActualFile( url );

                    if (file != null) {
                        url = file.toURI().toURL();
                    } else {
                        url = stripVFSFromNonJar( url ); 
                    }

                    if (url != null) {
                        urls.add( url );
                    }
                }
            }

        } catch (IllegalAccessException e) {
           e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return urls;
    }
    
    public static ClassLoaderFactory getFactory() {
        return new ClassLoaderFactory() {
            public ClassLoader newInstance( ClassLoader parent, Object mountMap ) {
                return new ImmutantClassLoader( parent, (TmpResourceMountMap)mountMap );
            }
        };
    }
    
    private File getActualFile(URL url) {
        String urlAsString = url.toExternalForm();
        int splitPoint = urlAsString.indexOf( ".jar/" ) + 5;
        String jarPrefix = urlAsString.substring( 0, splitPoint );
            
        return this.mountMap.getActualFile( jarPrefix );
    }
    
    private String getFileNameFromURL(URL url) {
        String urlAsString = url.toExternalForm();
        int splitPoint = urlAsString.indexOf( ".jar/" ) + 5;
        
        return urlAsString.substring( splitPoint );
    }
    
    private URL stripVFSFromNonJar(URL url) {
        try {
            URL nonVFSUrl = new URL( "file" + url.toExternalForm().substring( 3 ) );
            if ((new File( nonVFSUrl.toURI() )).exists()) {
                return nonVFSUrl;
            } 
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return null;
    }

    protected URL stripVFS(URL url) {
        if (url != null && "vfs".equals( url.getProtocol() )) {
            try {   
                File actualFile = getActualFile( url );
                if (actualFile != null) {
                    //translate resources within tmp mounted jars to the actual path. see IMMUTANT-70
                    url = new URL( "jar:" + actualFile.toURI().toURL().toExternalForm() + "!/" + getFileNameFromURL( url ) );
                } else {
                    return stripVFSFromNonJar( url );
                } 
            } catch (MalformedURLException e) {
                   e.printStackTrace();
            }
        }

        return url;
    }
    
    public String toString() {
        return ImmutantClassLoader.class.getName() + "[" + getParent().toString() + "]";
    }
    
    private TmpResourceMountMap mountMap;
    
}
