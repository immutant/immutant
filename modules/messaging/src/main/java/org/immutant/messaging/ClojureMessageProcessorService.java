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

import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.immutant.runtime.ClojureRuntime;
import org.projectodd.polyglot.messaging.BaseMessageProcessor;
import org.projectodd.polyglot.messaging.BaseMessageProcessorGroup;
import org.projectodd.polyglot.messaging.MessageProcessorService;

public class ClojureMessageProcessorService extends MessageProcessorService {
 
    ClojureMessageProcessorService(BaseMessageProcessorGroup group, BaseMessageProcessor listener, 
            ClojureRuntime runtime, Object setupHandler) {
        super( group, listener );
        this.runtime = runtime;
        this.setupHandler = setupHandler;
    }
    
    @SuppressWarnings({ "rawtypes" })
    @Override
    protected void setupConsumer() {
        Map settings = (Map)this.runtime.invoke( this.setupHandler );
        setSession( (Session)settings.get( "session") );
        setConsumer( (MessageConsumer)settings.get( "consumer" ) );
        ((MessageProcessor)getListener()).setHandler( settings.get( "handler" ) );
    }
    
    private ClojureRuntime runtime;
    private Object setupHandler;
}
