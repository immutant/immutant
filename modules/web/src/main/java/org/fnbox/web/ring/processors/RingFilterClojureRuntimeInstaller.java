package org.fnbox.web.ring.processors;

import org.fnbox.core.ClojureRuntime;
import org.fnbox.web.ring.RingMetaData;
import org.fnbox.web.servlet.RingFilter;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.web.deployment.ServletContextAttribute;

public class RingFilterClojureRuntimeInstaller implements DeploymentUnitProcessor {

    
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        
        if (!unit.hasAttachment( RingMetaData.ATTACHMENT_KEY )) {
            return;
        }
        
        ClojureRuntime runtime = unit.getAttachment( ClojureRuntime.ATTACHMENT_KEY );
        ServletContextAttribute runtimeAttr = new ServletContextAttribute( RingFilter.CLOJURE_RUNTIME, runtime );
        unit.addToAttachmentList( ServletContextAttribute.ATTACHMENT_KEY, runtimeAttr );
    }
    
    @Override
    public void undeploy(DeploymentUnit context) {

    }
}
