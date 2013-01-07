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

package org.immutant.web.ring.processors;

import org.apache.catalina.Context;
import org.immutant.core.processors.RegisteringProcessor;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.web.WebSubsystemServices;
import org.jboss.as.web.deployment.WarDeploymentProcessor;
import org.jboss.as.web.deployment.WarMetaData;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.Value;


public class WebContextRegisteringProcessor extends RegisteringProcessor {

    @Override
    public RegistryEntry registryEntry(DeploymentPhaseContext context) {
        DeploymentUnit unit = context.getDeploymentUnit();
        
        final WarMetaData warMetaData = unit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (warMetaData == null) {
            return null;
        }

        String hostName = WarDeploymentProcessor.hostNameOfDeployment( warMetaData, "default-host" );
        String pathName = WarDeploymentProcessor.pathNameOfDeployment( unit,
                warMetaData.getMergedJBossWebMetaData() );
        ServiceName serviceName = WebSubsystemServices.deploymentServiceName( hostName, pathName ); 
                        
        Context webContext = ((Value<Context>)unit.getServiceRegistry().getService( serviceName )).getValue();
        
        return new RegistryEntry( "web-context", webContext );
    }
}
