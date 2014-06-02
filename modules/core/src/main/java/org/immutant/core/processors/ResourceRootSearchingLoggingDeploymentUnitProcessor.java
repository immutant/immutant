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

import org.jboss.as.logging.LoggingDeploymentUnitProcessor;
import org.jboss.as.logging.LoggingLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.logmanager.LogContext;
import org.jboss.modules.Module;
import org.jboss.vfs.VirtualFile;

import java.lang.reflect.Method;
import java.util.List;

public class ResourceRootSearchingLoggingDeploymentUnitProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        if (unit.hasAttachment(this.delegate.LOG_CONTEXT_KEY)) {
            return;
        }
        final List<ResourceRoot> roots = unit.getAttachment(Attachments.RESOURCE_ROOTS);

        try {
            processDeploymentLogging(unit, roots);
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException("Failed to handle logging settings at root", e);
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    private void processDeploymentLogging(final DeploymentUnit unit, final List<ResourceRoot> roots) throws Exception {
        Class delegateClass = this.delegate.getClass();
        Method findConfigFile = delegateClass.getDeclaredMethod("findConfigFile", VirtualFile.class);
        findConfigFile.setAccessible(true);
        Method isLog4jConfiguration = delegateClass.getDeclaredMethod("isLog4jConfiguration", String.class);
        isLog4jConfiguration.setAccessible(true);
        Method configure = delegateClass.getDeclaredMethod("configure", VirtualFile.class, ClassLoader.class,
                                                           LogContext.class);
        configure.setAccessible(true);

        for (ResourceRoot root : roots) {
            final VirtualFile configFile = (VirtualFile)findConfigFile.invoke(this.delegate, root.getRoot());
            if (configFile != null) {
                LoggingLogger.ROOT_LOGGER.info("Applying logging settings from " + configFile.getPathName());
                final Module module = unit.getAttachment(Attachments.MODULE);
                final LogContext logContext;

                if ((Boolean)isLog4jConfiguration.invoke(null, configFile.getName())) {
                    logContext = LogContext.create(true);
                } else {
                    logContext = LogContext.create();
                }

                configure.invoke(this.delegate, configFile, module.getClassLoader(), logContext);

                return;
            }
        }
    }

    final private LoggingDeploymentUnitProcessor delegate = new LoggingDeploymentUnitProcessor();
}
