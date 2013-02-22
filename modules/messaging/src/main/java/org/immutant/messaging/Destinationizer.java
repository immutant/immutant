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
import org.immutant.runtime.ClojureRuntime;
import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.messaging.destinations.Destroyable;
import org.projectodd.polyglot.messaging.destinations.processors.QueueInstaller;
import org.projectodd.polyglot.messaging.destinations.processors.TopicInstaller;


public class Destinationizer extends AtRuntimeInstaller<Destinationizer> implements HasImmutantRuntimeInjector {

    public Destinationizer(DeploymentUnit unit) {
        super( unit );
    }

    public boolean createQueue(String queueName, boolean durable, String selector, Object callback) {
        if (destinationExists( queueName, true )) {
            return false;
        }
                
        QueueService service = new QueueService( queueName, selector, durable, 
                                                 this.clojureRuntimeInjector.getValue(),
                                                 callback );
                    
        this.destinations.put( queueName, 
                               QueueInstaller.deploy( getTarget(), service, queueName ) );
        
        return true;
    }
    
    public boolean createTopic(String topicName, Object callback) {
        if (destinationExists( topicName, false )) {
            return false;
        } 
        
        TopicService service = new TopicService( topicName, 
                                                 this.clojureRuntimeInjector.getValue(),
                                                 callback );
        
        this.destinations.put( topicName, 
                               TopicInstaller.deploy( getTarget(), service, topicName ) );
        
        return true;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean destroyDestination(String name, Object callback) {
        boolean success = false;
        
        this.messageProcessorGroupizerInjector.getValue().removeGroupsFor( name );
        
        ServiceName serviceName = this.destinations.get( name );
        if (serviceName != null) {
            ServiceController dest = getUnit().getServiceRegistry().getService( serviceName );
            if (dest != null) {
                Object service = dest.getService();
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

    protected boolean destinationExists(String name, boolean queue) {
        ServiceName defaultService = MessagingServices.getHornetQServiceName( "default" );
        ServiceName serviceName;
        if (queue) {
            serviceName = JMSServices.getJmsQueueBaseServiceName( defaultService );
        } else {
            serviceName = JMSServices.getJmsTopicBaseServiceName( defaultService );
        }
        serviceName = serviceName.append( name );
        
        return (getUnit().getServiceRegistry().getService( serviceName ) != null);
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
}
