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

import javax.jms.Message;

import org.projectodd.shimdandy.ClojureRuntimeShim;

/**
 * Only used when in-container but connecting to a remote destination.
 */
public class MessageListener implements javax.jms.MessageListener {
    
    public MessageListener(ClojureRuntimeShim runtime, Object handler) {
        this.runtime = runtime;
        this.fn = handler;
    }
    
    @Override
    public void onMessage(Message message) {
       this.runtime.invoke( this.fn, message );
    }
    
    private ClojureRuntimeShim runtime;
    private Object fn;
}
