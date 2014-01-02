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

package org.immutant.messaging;

import org.immutant.core.HasImmutantRuntimeInjector;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.core.ServiceSynchronizationManager;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.Destroyable;
import org.projectodd.polyglot.messaging.destinations.processors.QueueInstaller;
import org.projectodd.polyglot.messaging.destinations.processors.TopicInstaller;
import org.projectodd.shimdandy.ClojureRuntimeShim;

import java.util.HashMap;
import java.util.Map;

import static org.projectodd.polyglot.core.ServiceSynchronizationManager.*;


public class Destinationizer extends AtRuntimeInstaller<Destinationizer> implements HasImmutantRuntimeInjector {

    public Destinationizer(DeploymentUnit unit, ServiceTarget globalServiceTarget) {
        super( unit, globalServiceTarget );
    }

    public boolean createQueue(final String queueName, final boolean durable, final String selector) {
        if (DestinationUtils.destinationPointerExists(getUnit(), queueName)) {
            return false;
        }

        this.destinations.put(queueName,
                              QueueInstaller.deploySync(getUnit(),
                                                        getTarget(),
                                                        getGlobalTarget(),
                                                        queueName,
                                                        selector,
                                                        durable,
                                                        false));

        return true;
    }
    
    public boolean createTopic(String topicName) {
        if (DestinationUtils.destinationPointerExists(getUnit(), topicName)) {
            return false;
        }

        this.destinations.put(topicName,
                              TopicInstaller.deploySync(getUnit(),
                                                        getTarget(),
                                                        getGlobalTarget(),
                                                        topicName,
                                                        false));
        return true;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public synchronized boolean destroyDestination(String name) {
        boolean success = false;
        
        this.messageProcessorGroupizerInjector.getValue().removeGroupsFor( name );
        
        ServiceName serviceName = this.destinations.get( name );
        if (serviceName != null) {
            ServiceRegistry registry = getUnit().getServiceRegistry();
            ServiceController dest = registry.getService( serviceName );
            if (dest != null) {
                ServiceName globalName = QueueInstaller.queueServiceName(name);
                ServiceController globalDest = registry.getService(globalName);
                if (globalDest == null) {
                    globalName = TopicInstaller.topicServiceName(name);
                    globalDest = registry.getService(globalName);
                }
                if (globalDest == null) {
                    //should never happen, but...
                    throw new IllegalStateException("Failed to find global dest for " + name);
                }
                    
                Object service = globalDest.getService();
                //force it to destroy, even if it's durable
                if (service instanceof Destroyable) {
                    ((Destroyable)service).setShouldDestroy( true );
                }

                dest.setMode( Mode.REMOVE );

                ServiceSynchronizationManager mgr = INSTANCE;

                if (!mgr.waitForServiceRemove(serviceName,
                                              DestinationUtils.destinationWaitTimeout())) {
                    log.warn("Timed out waiting for " + name + " pointer to stop.");
                }

                if (mgr.hasService(globalName) &&
                        !mgr.hasDependents(globalName)) {
                    if (!mgr.waitForServiceDown(globalName,
                                                DestinationUtils.destinationWaitTimeout())) {
                        log.warn("Timed out waiting for " + name + " to stop.");
                    }

                }
                success = true;
            } 
                    
            this.destinations.remove(name);
        }
        
        return success;
    }

    @Override
    public Injector<ClojureRuntimeShim> getClojureRuntimeInjector() {
        return this.clojureRuntimeInjector;
    }
    
    public Injector<MessageProcessorGroupizer> getMessageProcessorGroupizerInjector() {
        return this.messageProcessorGroupizerInjector;
    }
    
    //useful for testing
    public Map<String, ServiceName> getDestinations() {
        return this.destinations;
    }
    
    private final InjectedValue<ClojureRuntimeShim> clojureRuntimeInjector = new InjectedValue<ClojureRuntimeShim>();
    private final InjectedValue<MessageProcessorGroupizer> messageProcessorGroupizerInjector = new InjectedValue<MessageProcessorGroupizer>();
    private Map<String, ServiceName> destinations = new HashMap<String, ServiceName>();
    
    static final Logger log = Logger.getLogger( "org.immutant.messaging" );
}
