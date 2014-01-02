/*
 * Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.MountHandle;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

public class TmpResourceMounter implements Service<TmpResourceMounter> {
    
    public TmpResourceMounter(DeploymentUnit unit, TmpResourceMountMap mountMap) {
        this.deploymentUnit = unit;
        this.mountMap = mountMap;
    }
    
    public ResourceRoot mount(File file) throws IOException {
        return mount( file, true );
    }
    
    public ResourceRoot mount(File file, boolean unmountable) throws IOException {
        synchronized (this.mountMap) {
            VirtualFile mountPath = tmpMountPath().getChild( file.getName() );
            log.debug( this.deploymentUnit.getName() + ": mounting " + file + " at " + mountPath );
            final ResourceRoot childResource = new ResourceRoot( mountPath, 
                    new MountHandle( VFS.mountZip( file, mountPath, TempFileProviderService.provider() ) ) );
            ModuleRootMarker.mark( childResource );
            this.deploymentUnit.addToAttachmentList( Attachments.RESOURCE_ROOTS, childResource );

            this.mountMap.put( mountPath.toURL(), childResource, file, unmountable );

            return childResource;
        }
    }

    public boolean unmount(URL mountURL) {
        return unmount( mountURL.toExternalForm() );
    }
    
    public boolean unmount(String mountPath) {
        synchronized (this.mountMap) {
            ResourceRoot root = this.mountMap.getResourceRoot( mountPath );
            if (root != null &&
                    this.mountMap.isUnmountable( mountPath )) {
                List<ResourceRoot> roots = this.deploymentUnit.getAttachmentList( Attachments.RESOURCE_ROOTS );
                roots.remove( root );
                root.getMountHandle().close();
                this.mountMap.remove( mountPath );

                return true;
            }
        }
        
        return false;
    }
    
    private VirtualFile tmpMountPath() throws IOException {
        ResourceRoot root = this.deploymentUnit.getAttachment( Attachments.DEPLOYMENT_ROOT );

        return root.getRoot().getChild( "tmp_mounts" ).getChild( this.deploymentUnit.getName() );
    }
    
    public TmpResourceMountMap getMountMap() {
        return this.mountMap;
    }
    
    @Override
    public TmpResourceMounter getValue() throws IllegalStateException,
            IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void stop(StopContext context) {

    }
    
    private DeploymentUnit deploymentUnit;
    private TmpResourceMountMap mountMap;
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.core" );

}
