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

package org.immutant.core.processors;

import org.immutant.core.ClojureMetaData;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class INFMounter implements DeploymentUnitProcessor {
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        ClojureMetaData metaData = deploymentUnit.getAttachment(ClojureMetaData.ATTACHMENT_KEY);

        if (metaData == null) {
            return;
        }

        List<String> resourceDirs = deploymentUnit.getAttachmentList(AppDependenciesProcessor.APP_RESOURCE_PATHS);

        if (resourceDirs != null) {
            VirtualFile root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();

            try {
                mountINFDir(root, resourceDirs, "META-INF");
                mountINFDir(root, resourceDirs, "WEB-INF");
            } catch (Exception e) {
                throw new DeploymentUnitProcessingException(e);
            }
        }
    }

    private void mountINFDir(VirtualFile root, List<String> resourceDirs, String name) throws IOException {
        if (!root.getChild(name).exists()) {
            VirtualFile infDir = null;
            for(String dir : resourceDirs) {
                // lein puts a pom in a META-INF under target/classes, so we skip that
                if (dir.endsWith("target/classes")) {
                    log.trace("skipping " + dir);
                    continue;
                }

                infDir = VFS.getChild(dir).getChild(name);
                log.trace("looking at " + infDir);
                if (infDir.exists()) {
                    log.trace("found " + infDir);
                    break;
                }
            }

            if (infDir != null && infDir.exists()) {
                log.trace("mounting " + infDir);
                this.closeables.add(VFS.mountReal(infDir.getPhysicalFile(), root.getChild(name)));
            }

        } else {
            log.trace(root.getChild(name) + " already exists!");
        }
    }


    @Override
    public void undeploy(DeploymentUnit context) {
        for (Closeable each : this.closeables) {
            try {
                log.info("TC: closing " + each);
                each.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private final List<Closeable> closeables = new ArrayList<Closeable>();

    private static final Logger log = Logger.getLogger(INFMounter.class);
}
