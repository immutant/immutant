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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.immutant.core.ClojureMetaData;
import org.immutant.core.Immutant;
import org.immutant.core.as.CoreServices;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.MountHandle;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.logging.Logger;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VisitorAttributes;
import org.jboss.vfs.util.SuffixMatchFilter;
import org.projectodd.polyglot.core.as.KnobDeploymentMarker;

public class AppJarScanningProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        ClojureMetaData metaData = unit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );
        if (metaData == null && 
                !KnobDeploymentMarker.isMarked( unit )) {
            return;
        }
        
        ResourceRoot resourceRoot = unit.getAttachment( Attachments.DEPLOYMENT_ROOT );
        VirtualFile root = resourceRoot.getRoot();

        try {

            boolean clojureProvided = false;
            
            for (String scanRoot : SCAN_ROOTS) {
                for (VirtualFile child : getJarFiles( root.getChild( scanRoot ) )) {
                    final Closeable closable = child.isFile() ? mount( child, false ) : null;
                    if (child.getName().matches( "^clojure(-\\d.\\d.\\d)?\\.jar$" )) {
                        clojureProvided = true;
                    }
                    final MountHandle mountHandle = new MountHandle( closable );
                    final ResourceRoot childResource = new ResourceRoot( child, mountHandle );
                    ModuleRootMarker.mark(childResource);
                    unit.addToAttachmentList( Attachments.RESOURCE_ROOTS, childResource );
                }
            }

            if (!clojureProvided) {
                Immutant immutant = (Immutant)phaseContext.getServiceRegistry().getService( CoreServices.IMMUTANT ).getValue();
                
                log.warn( "No clojure.jar found within " + metaData.getApplicationName() + 
                        ", Using built-in clojure.jar (v" + immutant.getClojureVersion() + ")" );

                // FIXME this is ugly as fuck, and needs to be cleaned up

                // borrow the shipped clojure.jar
                String jarPath = System.getProperty( "jboss.home.dir" ) + "/modules/org/immutant/core/main/clojure.jar";
                VirtualFile jar = VFS.getChild( jarPath );
                
                // mount it at a tmp location so it can be mounted more than once
                VirtualFile mountPath = VFS.getChild( File.createTempFile( metaData.getApplicationName(), "clojure.jar" ).getAbsolutePath() );
                final MountHandle mountHandle = new MountHandle( mount( jar, mountPath, false ) );
                final ResourceRoot childResource = new ResourceRoot( mountPath, mountHandle );
                ModuleRootMarker.mark(childResource);
                unit.addToAttachmentList( Attachments.RESOURCE_ROOTS, childResource );
            }
                            
            for(String each : DIR_ROOTS) {
                final ResourceRoot childResource = new ResourceRoot( root.getChild( each ), null );
                ModuleRootMarker.mark(childResource);
                unit.addToAttachmentList( Attachments.RESOURCE_ROOTS, childResource );
            }
            
        } catch (IOException e) {
            log.error( "Error processing jars", e );
        }

        
    }
    
    public List<VirtualFile> getJarFiles(VirtualFile dir) throws IOException {
        return dir.getChildrenRecursively( new NonDevJarFilter( dir ) );
    }
    
    private static Closeable mount(VirtualFile moduleFile, boolean explode) throws IOException {
        return mount( moduleFile, moduleFile, explode );
    }
    
    private static Closeable mount(VirtualFile moduleFile, VirtualFile mountPoint, boolean explode) throws IOException {
        return explode ? VFS.mountZipExpanded( moduleFile, mountPoint, TempFileProviderService.provider() )
                       : VFS.mountZip( moduleFile, mountPoint, TempFileProviderService.provider() );
    }
    
    @Override
    public void undeploy(DeploymentUnit context) {
        
    }
    
    @SuppressWarnings("serial")
    private static final List<String> SCAN_ROOTS = new ArrayList<String>() {
        {
            add( "lib" );
        }
    };
    
    @SuppressWarnings("serial")
    private static final List<String> DIR_ROOTS = new ArrayList<String>() {
        {
            // FIXME: we really should pull this dynamically from lein's project.clj
            add( "src" );
            add( "resources" );
            add( "classes" );
        }
    };
   
    
    private static final Logger log = Logger.getLogger( "org.immutant.core" );

    class NonDevJarFilter extends SuffixMatchFilter {
        NonDevJarFilter(VirtualFile rootDir) {
            super( ".jar", VisitorAttributes.DEFAULT );
            this.rootDir = rootDir;
        }
        

        @Override
        public boolean accepts(VirtualFile file) {
            return super.accepts( file ) && 
                    !file.getPathNameRelativeTo( this.rootDir ).startsWith( "dev/" );
        }
        
        private VirtualFile rootDir;
    }
    
}
