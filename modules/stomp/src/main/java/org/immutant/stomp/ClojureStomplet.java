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
import org.projectodd.stilts.MessageSink;
import org.projectodd.stilts.stomp.StompException;
import org.projectodd.stilts.stomp.StompMessage;
import org.projectodd.stilts.stomp.spi.StompSession;
import org.projectodd.stilts.stomplet.Stomplet;
import org.projectodd.stilts.stomplet.StompletConfig;
import org.projectodd.stilts.stomplet.Subscriber;
import org.projectodd.stilts.stomplet.container.SimpleStompletContainer;

public class ClojureStomplet implements Stomplet, Service<Stomplet> {

    public ClojureStomplet(ClojureRuntime runtime, String route, Object handler) {
        this.runtime = runtime;
        this.route = route;
        this.handler = handler;
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
    public void onMessage(StompMessage message, StompSession session) throws StompException {
        invokeHandler( "message", session, message );
    }

    @Override
    public void onSubscribe(Subscriber subscriber) throws StompException {
        invokeHandler( "subscribe", subscriber.getSession(), subscriber );
    }

    @Override
    public void onUnsubscribe(Subscriber subscriber) throws StompException {
        invokeHandler( "unsubscribe", subscriber.getSession(), subscriber );
    }
    
    private void invokeHandler(String op, StompSession session, Object payload) {
        this.runtime.invoke( this.handler, op, session, payload );     
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
    private Object handler;
        
    private InjectedValue<SimpleStompletContainer> containerInjector = new InjectedValue<SimpleStompletContainer>();

    
}
