/*
 * Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

package org.immutant.stomp;

import org.immutant.runtime.ClojureRuntime;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.stilts.stomp.StompException;
import org.projectodd.stilts.stomp.StompMessage;
import org.projectodd.stilts.stomp.spi.StompSession;
import org.projectodd.stilts.stomplet.Stomplet;
import org.projectodd.stilts.stomplet.StompletConfig;
import org.projectodd.stilts.stomplet.Subscriber;
import org.projectodd.stilts.stomplet.container.SimpleStompletContainer;

public class ClojureStomplet implements Stomplet, Service<Stomplet> {

    public ClojureStomplet(ClojureRuntime runtime, String route, Object messageHandler, Object subscribeHandler, Object unsubscribeHandler) {
        this.runtime = runtime;
        this.route = route;
        this.messageHandler = messageHandler;
        this.subscribeHandler = subscribeHandler;
        this.unsubscribeHandler = unsubscribeHandler;
    }

    @Override
    public void destroy() throws StompException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void initialize(StompletConfig arg0) throws StompException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onMessage(StompMessage arg0, StompSession arg1)
            throws StompException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onSubscribe(Subscriber arg0) throws StompException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onUnsubscribe(Subscriber arg0) throws StompException {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public Stomplet getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        SimpleStompletContainer container = this.containerInjector.getValue();
        try {
            container.addStomplet( this.route, this );
        } catch (StompException e) {
            throw new StartException( e );
        }
    }

    @Override
    public void stop(StopContext context) {
        SimpleStompletContainer container = this.containerInjector.getValue();
        try {
            container.removeStomplet( this.route );
        } catch (StompException e) {
            e.printStackTrace();
        }
    }

    public Injector<SimpleStompletContainer> getStompletContainerInjector() {
        return this.containerInjector;
    }
    
    private ClojureRuntime runtime;
    private String route;
    private Object messageHandler;
    private Object subscribeHandler;
    private Object unsubscribeHandler;
    
    private InjectedValue<SimpleStompletContainer> containerInjector = new InjectedValue<SimpleStompletContainer>();

    
}
