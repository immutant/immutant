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

package org.immutant.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.immutant.core.HasImmutantRuntimeInjector;
import org.immutant.core.SimpleServiceStateListener;
import org.immutant.runtime.ClojureRuntime;
import org.jboss.as.messaging.jms.JMSQueueService;
import org.jboss.as.messaging.jms.JMSTopicService;
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
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.Destroyable;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSQueueService;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSTopicService;
import org.projectodd.polyglot.messaging.destinations.processors.QueueInstaller;
import org.projectodd.polyglot.messaging.destinations.processors.TopicInstaller;


public class Destinationizer extends AtRuntimeInstaller<Destinationizer> implements HasImmutantRuntimeInjector {

    public Destinationizer(DeploymentUnit unit, ServiceTarget globalServiceTarget) {
        super( unit, globalServiceTarget );
    }

    public boolean createQueue(final String queueName, final boolean durable, final String selector, Object callback) {
        if (DestinationUtils.destinationPointerExists(getUnit(), queueName)) {
            return false;
        }
                            
        JMSQueueService queue =
                QueueInstaller.deployGlobalQueue(getUnit().getServiceRegistry(), 
                                                 getGlobalTarget(),
                                                 queueName, 
                                                 durable,   
                                                 selector, 
                                                 DestinationUtils.jndiNames( queueName, false ));
        
        createDestinationService(queueName, callback,
                                 QueueInstaller.queueServiceName(queueName),
                                 queue instanceof DestroyableJMSQueueService ?
                                         ((DestroyableJMSQueueService)queue).getReferenceCount() :
                                         null);
        
        return true;
    }
    
    public boolean createTopic(String topicName, Object callback) {
        if (DestinationUtils.destinationPointerExists(getUnit(), topicName)) {
            return false;
        }

        JMSTopicService topic =
                TopicInstaller.deployGlobalTopic(getUnit().getServiceRegistry(),
                                                 getGlobalTarget(),
                                                 topicName,
                                                 DestinationUtils.jndiNames( topicName, false ));
        
        createDestinationService(topicName, callback,
                                 TopicInstaller.topicServiceName(topicName),
                                 topic instanceof DestroyableJMSTopicService ?
                                         ((DestroyableJMSTopicService) topic).getReferenceCount() :
                                         null);

        return true;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean destroyDestination(String name, Object callback) {
        boolean success = false;
        
        this.messageProcessorGroupizerInjector.getValue().removeGroupsFor( name );
        
        ServiceName serviceName = this.destinations.get( name );
        if (serviceName != null) {
            ServiceRegistry registry = getUnit().getServiceRegistry();
            ServiceController dest = registry.getService( serviceName );
            if (dest != null) {
                ServiceController globalDest = 
                        registry.getService( QueueInstaller.queueServiceName( name ) );
                if (globalDest == null) {
                    globalDest = registry.getService( TopicInstaller.topicServiceName( name ) );
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
                
                dest.addListener( new SimpleServiceStateListener( this.clojureRuntimeInjector.getValue(),
                                                                  callback ) );
                dest.setMode( Mode.REMOVE );
                success = true;
            } 
                    
            this.destinations.remove( name );
        }
        
        return success;
    }

    protected void createDestinationService(String destName, Object callback, 
                                            ServiceName globalName,
                                            AtomicInteger referenceCount) {
        this.destinations.put(destName, 
                              DestinationUtils.deployDestinationPointerService(getUnit(), 
                                                                               getTarget(), 
                                                                               destName,
                                                                               globalName,
                                                                               referenceCount,
                                                                               new SimpleServiceStateListener(this.clojureRuntimeInjector.getValue(), 
                                                                                                              callback)));
    }
    
    
    @Override
    public Injector<ClojureRuntime> getClojureRuntimeInjector() {
        return this.clojureRuntimeInjector;
    }
    
    public Injector<MessageProcessorGroupizer> getMessageProcessorGroupizerInjector() {
        return this.messageProcessorGroupizerInjector;
    }
    
    //useful for testing
    public Map<String, ServiceName> getDestinations() {
        return this.destinations;
    }
    
    private final InjectedValue<ClojureRuntime> clojureRuntimeInjector = new InjectedValue<ClojureRuntime>();
    private final InjectedValue<MessageProcessorGroupizer> messageProcessorGroupizerInjector = new InjectedValue<MessageProcessorGroupizer>();
    private Map<String, ServiceName> destinations = new HashMap<String, ServiceName>();
    
    static final Logger log = Logger.getLogger( "org.immutant.messaging" );
}
