/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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


public class MessageProcessorMetaData {
    
    public MessageProcessorMetaData(String dest, Runnable handler, Map<String,?> opts) {
        this.destinationName = dest;
        this.handler = handler;
        this.filter = (String) opts.get("filter");
        this.concurrency = (Integer) opts.get("concurrency");
        this.durable = (Boolean) opts.get("durable");
    }

    public String getName() {
        return getDestinationName();
    }

    public String getDestinationName() {
        return this.destinationName;
    }

    public Runnable getHandler() {
        return this.handler;
    }

    public String getFilter() {
        return this.filter;
    }

    public Integer getConcurrency() {
        return this.concurrency;
    }

    public Boolean getDurable() {
        return this.durable;
    }

    private String destinationName;
    private Runnable handler;
    private String filter;
    private int concurrency = 1;
    private boolean durable = false;

}
