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

package org.fnbox.web.ring;

import org.fnbox.core.ClojureApplicationMetaData;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.logging.Logger;
import org.projectodd.polyglot.core.processors.FileLocatingProcessor;

public class RingApplicationRecognizer extends FileLocatingProcessor {

    //public static final String DEFAULT_RACKUP_PATH = "config.ru";

    public RingApplicationRecognizer() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        //ResourceRoot resourceRoot = unit.getAttachment( Attachments.DEPLOYMENT_ROOT );
        //VirtualFile root = resourceRoot.getRoot();

        ClojureApplicationMetaData appMetaData = unit.getAttachment( ClojureApplicationMetaData.ATTACHMENT_KEY );
          
        if (appMetaData != null) {
        //FIXME: for now, we assume everything is a ring app
            RingApplicationMetaData ringMetaData = new RingApplicationMetaData( appMetaData );
            unit.putAttachment( RingApplicationMetaData.ATTACHMENT_KEY, ringMetaData );
        }
         
//        if (isRackApplication( root )) {
//            RingApplicationMetaData ringAppMetaData = unit.getAttachment( RingApplicationMetaData.ATTACHMENT_KEY );
//
//            if (ringAppMetaData == null) {
//                ringAppMetaData = new RingApplicationMetaData();
//                unit.putAttachment( RingApplicationMetaData.ATTACHMENT_KEY, ringAppMetaData );
//            }
//        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }


//     static boolean isRackApplication(VirtualFile file) {
//        boolean result = hasAnyOf( file, DEFAULT_RACKUP_PATH );
//        return result;
//    }

    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger( "org.fnbox.web.rack" );
}
