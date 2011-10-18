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

package org.immutant.core.processors;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.immutant.core.ClojureMetaData;
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

/**
 * Handle mounting .clj files and marking them as a DEPLOYMENT_ROOT
 * FIXME: This doesn't handle archives
 * 
 */
public class AppCljParsingProcessor implements DeploymentUnitProcessor {
    
    public AppCljParsingProcessor() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        String deploymentName = deploymentUnit.getName();
        
        try {
            VirtualFile cljFile = getFile( deploymentUnit );
            if (cljFile == null) {
                return;
            }
            
            ClojureMetaData appMetaData = new ClojureMetaData( deploymentName, 
                    ClojureMetaData.parse(  cljFile.getPhysicalFile() ) );
            
            appMetaData.attachTo( deploymentUnit );
                        
            VirtualFile root = appMetaData.getRoot();
            ResourceRoot appRoot;
            
            if (root == null) {
                throw new DeploymentUnitProcessingException( "No application root specified." );
            }
            
            if ( ! root.exists() ) {
                throw new DeploymentUnitProcessingException( "Application root does not exist: " + root.toURL().toExternalForm() );
            }
            
            if (appMetaData.getAppFunction() == null) {
                throw new DeploymentUnitProcessingException( "No app-function specified." );
            }
            
            if (root.exists() && !root.isDirectory()) {
                // Expand the referenced root if it's not a directory (ie .knob archive)
                final Closeable closable = VFS.mountZipExpanded( root, root, TempFileProviderService.provider() );
                final MountHandle mountHandle = new MountHandle( closable );
                appRoot = new ResourceRoot( root, mountHandle );
            } else {
                appRoot = new ResourceRoot( root, null );
            }
            deploymentUnit.putAttachment( Attachments.DEPLOYMENT_ROOT, appRoot );

        } catch (Exception e) {
            throw new DeploymentUnitProcessingException( e );
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
       
    }
    
    protected VirtualFile getFile(DeploymentUnit unit) throws DeploymentUnitProcessingException, IOException {
        List<VirtualFile> matches = new ArrayList<VirtualFile>();

        ResourceRoot resourceRoot = unit.getAttachment( Attachments.DEPLOYMENT_ROOT );
        VirtualFile root = resourceRoot.getRoot();
        
        if (this.knobFilter.accepts( root )) {
            return root;
        }

        matches = root.getChildren( this.knobFilter );

        if (matches.size() > 1) {
            throw new DeploymentUnitProcessingException( "Multiple application clj files found in " + root );
        }

        VirtualFile file = null;
        if (matches.size() == 1) {
            file = matches.get( 0 );
//            if (file.getName().endsWith( "-rails.yml" )) {
//                logDeprecation( unit, "Usage of -rails.yml is deprecated, please rename to -knob.yml: " + file );
//            } else if (file.getName().endsWith( "-rack.yml" )) {
//                logDeprecation( unit, "Usage of -rack.yml is deprecated, please rename to -knob.yml: " + file );
//            }
        }

        return file;
    }
    
    private VirtualFileFilter knobFilter = (new VirtualFileFilter() {
            public boolean accepts(VirtualFile file) {
                return file.getName().endsWith( ".clj" );
            }
        });

    
    static final Logger log = Logger.getLogger( "org.immutant.core" );
}
