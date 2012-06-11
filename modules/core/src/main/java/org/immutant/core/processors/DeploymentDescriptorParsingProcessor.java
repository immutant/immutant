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

package org.immutant.core.processors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.immutant.core.ClojureMetaData;
import org.immutant.core.Immutant;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.MountHandle;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.logging.Logger;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;

public class DeploymentDescriptorParsingProcessor implements DeploymentUnitProcessor {
    
    public DeploymentDescriptorParsingProcessor() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        
        if (deploymentUnit.hasAttachment( ClojureMetaData.ATTACHMENT_KEY )) {
            return;
        }

        String deploymentName = deploymentUnit.getName();

        try {
            VirtualFile descriptor = getDescriptorFile( deploymentUnit );
            if (descriptor == null) {
                return;
            }
            
            ClojureMetaData appMetaData = new ClojureMetaData( deploymentName, 
                    ClojureMetaData.parse( descriptor.getPhysicalFile() ) );
            
            appMetaData.attachTo( deploymentUnit );
                        
            File root = appMetaData.getRoot();
            
            if (root == null) {
                throw new DeploymentUnitProcessingException( "No application root specified." );
            }
            
            if (!root.exists()) {
                throw new DeploymentUnitProcessingException( "Application root does not exist: " + root.getAbsolutePath() );
            }


            VirtualFile virtualRoot = VFS.getChild( root.toURI() );
            MountHandle mountHandle = null;

            if (!root.isDirectory()) {
                // Expand the referenced root if it's not a directory (ie .ima archive)
                mountHandle = new MountHandle( VFS.mountZipExpanded( virtualRoot, virtualRoot, TempFileProviderService.provider() ) );
            }
            
            deploymentUnit.putAttachment( Attachments.DEPLOYMENT_ROOT, new ResourceRoot( virtualRoot, mountHandle ) );
            deploymentUnit.putAttachment( ClojureMetaData.DESCRIPTOR_FILE, descriptor.getPhysicalFile() );
            
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException( e );
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
    
    protected VirtualFile getDescriptorFile(DeploymentUnit unit) throws DeploymentUnitProcessingException, IOException {
        List<VirtualFile> matches = new ArrayList<VirtualFile>();

        ResourceRoot resourceRoot = unit.getAttachment( Attachments.DEPLOYMENT_ROOT );
        if (resourceRoot == null) {
            return null;
        }
        VirtualFile root = resourceRoot.getRoot();
        
        if (this.descriptorFilter.accepts( root )) {
            return root;
        }

        matches = root.getChildren( this.descriptorFilter );

        if (matches.size() > 1) {
            throw new DeploymentUnitProcessingException( "Multiple Immutant descriptors found in " + root );
        }

        VirtualFile file = null;
        if (matches.size() == 1) {
            file = matches.get( 0 );
        }

        return file;
    }
    
    private VirtualFileFilter descriptorFilter = (new VirtualFileFilter() {
            public boolean accepts(VirtualFile file) {
                return file.getName().endsWith( Immutant.DESCRIPTOR_SUFFIX );
            }
        });

    
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
