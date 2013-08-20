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

import java.util.Map;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Connection;
import javax.jms.XAConnection;

import org.immutant.core.HasImmutantRuntimeInjector;
import org.immutant.core.SimpleServiceStateListener;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.ServiceSynchronizationManager;
import org.projectodd.polyglot.messaging.BaseMessageProcessor;
import org.projectodd.polyglot.messaging.BaseMessageProcessorGroup;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.shimdandy.ClojureRuntimeShim;

public class MessageProcessorGroup extends BaseMessageProcessorGroup implements MessageProcessorGroupMBean, HasImmutantRuntimeInjector {

    public MessageProcessorGroup(ServiceRegistry registry, 
                                 ServiceName baseServiceName,
                                 String destinationName, 
                                 Connection connection, 
                                 Object setupHandler) {
        super( registry, baseServiceName, destinationName, MessageProcessor.class );
        setStartAsynchronously( false );
        setConnection( connection );
        setXAEnabled(connection instanceof XAConnection);
        this.setupHandler = setupHandler;
    }
    
    @Override
    protected BaseMessageProcessor instantiateProcessor() {
        return new MessageProcessor( getRuntime() );
    }
    
    @Override
    protected void startConnection(StartContext context) {
        try {
            getConnection().start();
        } catch (JMSException e) {
            context.failed( new StartException( e ) );
        }
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    protected Session createSession() {
        //TODO: use the new method for creating sessions that supposedly fixes IMMUTANT-203
        // maybe take a look at the method we're overriding here
        this.setupData = (Map)getRuntime().invoke( this.setupHandler );
        
        return (Session)this.setupData.get("session");
    }
    
    @Override 
    protected MessageConsumer createConsumer(Session session) {
        return (MessageConsumer)getRuntime().invoke( this.setupData.get( "consumer-fn" ), session );
    }
    
    @Override 
    protected void startConsumer(BaseMessageProcessor processor) throws Exception {
        super.startConsumer(processor);
        ((MessageProcessor)processor).setHandler( this.setupData.get("handler") );
    }
    
    public void remove(boolean wait) {
        ServiceController service = getServiceRegistry().getService( getBaseServiceName() );
        if (service != null) {
            service.setMode( Mode.REMOVE );

            if (wait) {
                if (!ServiceSynchronizationManager.INSTANCE
                        .waitForServiceDown(getBaseServiceName(),
                                            DestinationUtils.destinationWaitTimeout())) {
                    log.warn("Timed out waiting for " + getName() + " listener to stop.");
                }
            }

        }
    }

    public void remove() {
        remove(false);
    }
    
    public ClojureRuntimeShim getRuntime() {
        return this.clojureRuntimeInjector.getValue();
    }
    
    @Override
    public Injector<ClojureRuntimeShim> getClojureRuntimeInjector() {
        return clojureRuntimeInjector;
    }
    
    private final InjectedValue<ClojureRuntimeShim> clojureRuntimeInjector = new InjectedValue<ClojureRuntimeShim>();
    private Object setupHandler;
    @SuppressWarnings("rawtypes")
    private Map setupData;
}
