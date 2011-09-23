package org.torquebox.clojure.web.ring;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.web.deployment.ServletContextAttribute;
import org.torquebox.clojure.core.ClojureRuntime;
import org.torquebox.clojure.web.servlet.RingFilter;

public class RingFilterClojureRuntimeInstaller implements DeploymentUnitProcessor {

    
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        
        if (unit.getAttachment( RingApplicationMetaData.ATTACHMENT_KEY ) == null) {
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
