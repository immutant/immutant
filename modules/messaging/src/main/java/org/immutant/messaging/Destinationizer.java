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

import org.immutant.core.HasImmutantRuntimeInjector;
import org.immutant.core.SimpleServiceStateListener;
import org.immutant.messaging.as.MessagingServices;
import org.immutant.runtime.ClojureRuntime;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
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

    @SuppressWarnings("rawtypes")
    public boolean createQueue(final String queueName, final boolean durable, final String selector, Object callback) {
        if (destinationExists( queueName )) {
            return false;
        }
                            
        ServiceName globalQServiceName = QueueInstaller.queueServiceName( queueName );
        ServiceController globalQService = 
                getUnit().getServiceRegistry().getService( globalQServiceName );
        
        if (globalQService == null) {
            deployGlobalQueue(queueName, durable, selector);
        } else {
            //handle reconfiguration of an existing queue
            DestroyableJMSQueueService actual = (DestroyableJMSQueueService)globalQService.getService();
            if (actual.isDurable() != durable || 
                    !actual.getSelector().equals( selector )) {
                String from = "durable: " + actual.isDurable() + ", selector: " + actual.getSelector();
                String to = "durable: " + durable + ", selector: " + selector;
                State currentState = globalQService.getState();     
                if (currentState == State.DOWN || 
                        currentState == State.STOPPING) {
                    log.info("Reconfiguring " + queueName + " from " + from + " to " + to);
                    replaceService(globalQServiceName, new Runnable() {
                        public void run() {
                            deployGlobalQueue(queueName, durable, selector);
                        }
                    });
                } else {
                    throw new IllegalStateException("Can't reconfigure " + queueName + " from " + from 
                                                    + " to " + to + " - it has already been configured");
                }
            }   
        }
    
        createDestinationService(queueName, callback, globalQServiceName);
        
        return true;
    }
    
    protected ServiceName deployGlobalQueue(String queueName, boolean durable, String selector) {
        return QueueInstaller.deploy(getGlobalTarget(), 
                                     new DestroyableJMSQueueService(queueName, selector, durable, 
                                                                        new String[] { DestinationUtils.jndiName( queueName ) }),
                                     queueName,    
                                     Mode.ON_DEMAND); 
    }
    
    public boolean createTopic(String topicName, Object callback) {
        if (destinationExists( topicName )) {
            return false;
        } 

        ServiceName globalTopicServiceName = TopicInstaller.topicServiceName( topicName );
        if (getUnit().getServiceRegistry().getService( globalTopicServiceName ) == null) {
            TopicInstaller.deploy(getGlobalTarget(), 
                                  new DestroyableJMSTopicService(topicName,  
                                                                    new String[] { DestinationUtils.jndiName( topicName ) }),
                                  topicName, 
                                  Mode.ON_DEMAND); 
        }
    
        createDestinationService(topicName, callback, globalTopicServiceName);

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

    protected void createDestinationService(String destName, Object callback, ServiceName globalName) {
        DestinationService service = 
                new DestinationService(destName,
                                       this.clojureRuntimeInjector.getValue(),
                                       callback);
                
        ServiceName serviceName = MessagingServices.destinationPointer(getUnit(), destName);
        
        build(serviceName, service, false)
            .addDependency( globalName )
            .install();
            
        this.destinations.put( destName, serviceName );
        
    }
    
    protected boolean destinationExists(String name) {
        return (getUnit().getServiceRegistry().getService(MessagingServices.destinationPointer(getUnit(), name)) 
                != null);
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
