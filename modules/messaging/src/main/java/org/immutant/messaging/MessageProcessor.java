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

import javax.jms.Message;
import javax.jms.XASession;
import javax.transaction.TransactionManager;

import org.jboss.logging.Logger;
import org.projectodd.polyglot.messaging.BaseMessageProcessor;
import org.projectodd.shimdandy.ClojureRuntimeShim;


public class MessageProcessor extends BaseMessageProcessor {
    
    public MessageProcessor(ClojureRuntimeShim runtime) {
        this.runtime = runtime;
    }
    
    @Override
    protected void prepareTransaction() {
        try {
            getTransactionManager().begin();
            getTransactionManager().getTransaction().enlistResource(((XASession)getSession()).getXAResource());
        } catch (Throwable e) {
            log.error("Failed to prepare transaction for message", e);
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            try {
                this.runtime.invoke(this.handler, message);
                if (isXAEnabled()) {
                    getTransactionManager().commit();
                }
            } catch (javax.transaction.RollbackException ignored) {
            } catch (Throwable e) {
                e.printStackTrace();
                if (isXAEnabled()) {
                    getTransactionManager().rollback();
                }
                throw(e);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Unexpected error processing message from: " + getGroup().getName(), e);
        }
    }
    
    public void setHandler(Object handler) {
        this.handler = handler;
    }
    
    private ClojureRuntimeShim runtime;
    private Object handler;
    private TransactionManager tm;
    static final Logger log = Logger.getLogger( MessageProcessor.class );
}
