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
import java.util.Collections;
import java.util.List;

import org.immutant.core.ApplicationBootstrapUtils;
import org.immutant.core.ClojureMetaData;
import org.immutant.core.Immutant;
import org.immutant.core.Timer;
import org.immutant.core.TmpResourceMounter;
import org.immutant.core.as.CoreServices;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.logging.Logger;
import org.jboss.vfs.VFS;
import org.projectodd.polyglot.core.as.ArchivedDeploymentMarker;

public class AppDependenciesProcessor implements DeploymentUnitProcessor {

    @SuppressWarnings("unchecked")
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        
        ClojureMetaData metaData = unit.getAttachment( ClojureMetaData.ATTACHMENT_KEY );
        if (metaData == null && 
                !ArchivedDeploymentMarker.isMarked( unit )) {
            return;
        }

        Timer t = new Timer("processing dependencies");
        TmpResourceMounter mounter = (TmpResourceMounter)unit.getServiceRegistry()
                .getService( CoreServices.tmpResourceMounter( unit ) ).getValue();
        
        File root = metaData.getRoot();
        
        try {
            List<File> dependencyJars = ApplicationBootstrapUtils.getDependencies( root, 
                    metaData.resolveDependencies(),
                    metaData.getLeinProfiles() );

            boolean clojureProvided = false;

            for (File each : dependencyJars) {
                if (each.getName().matches( "^clojure(-\\d.\\d.\\d)?\\.jar$" )) {
                    clojureProvided = true;
                }
                mounter.mount( each );
            }

            if (!clojureProvided) {
                Immutant immutant = (Immutant)phaseContext.getServiceRegistry().getService( CoreServices.IMMUTANT ).getValue();

                log.warn( "No clojure.jar found within " + metaData.getApplicationName() + 
                        ", Using built-in clojure.jar (v" + immutant.getClojureVersion() + ")" );

                // borrow the shipped clojure.jar
                String jarPath = System.getProperty( "jboss.home.dir" ) + "/modules/org/immutant/core/main/clojure.jar";
                mounter.mount( new File( jarPath ), false );
            }

            //mount the runtime jar
            String runtimePath = System.getProperty( "jboss.home.dir" ) + "/modules/org/immutant/core/main/immutant-runtime-impl.jar";
            mounter.mount( new File( runtimePath ), false );
            
            for(String each : ApplicationBootstrapUtils.resourceDirs( root, metaData.getLeinProfiles() )) {
                final ResourceRoot childResource = new ResourceRoot( VFS.getChild( each ), null );
                ModuleRootMarker.mark(childResource);
                unit.addToAttachmentList( Attachments.RESOURCE_ROOTS, childResource );
            }
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException( e );
        }
        
        // disable the annotation index, since we're not an EE app and it spits nasty WARNs to the log if
        // ring is included as an app dep in relation to some jetty classes
        unit.putAttachment( Attachments.COMPUTE_COMPOSITE_ANNOTATION_INDEX, false );
        // AS let's us disable the index, but then assumes it's always there, so we give it an empty one
        unit.putAttachment( Attachments.COMPOSITE_ANNOTATION_INDEX, new CompositeIndex( Collections.EMPTY_LIST ) );
        
        t.done();
    }

    @Override
    public void undeploy(DeploymentUnit unit) {
       
    }

    private static final Logger log = Logger.getLogger( "org.immutant.core" );

}
