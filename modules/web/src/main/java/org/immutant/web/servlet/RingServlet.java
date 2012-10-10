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
package org.immutant.web.servlet;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.servlets.DefaultServlet;

import org.immutant.runtime.ClojureRuntime;
import org.jboss.logging.Logger;

public class RingServlet extends DefaultServlet {
    public static final String CLOJURE_RUNTIME = "clojure.runtime";
    
    
    @Override
    public void init() throws ServletException {
        super.init();
        ServletConfig config = getServletConfig();
        this.runtime = (ClojureRuntime)getServletContext().getAttribute( CLOJURE_RUNTIME );
        this.handlerName = config.getServletName();
    }
    
    protected void serveResource(HttpServletRequest request,
            HttpServletResponse response,
            boolean ignored)
                    throws IOException, ServletException {
        doRing( request, response );    
    }
    
    protected void doRing(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try { 
            this.runtime.invoke( "immutant.web.ring/handle-request", this.handlerName, request, response );
        } catch (Exception e) {
            log.error( "Error invoking Ring filter", e );
            throw new ServletException( e );
        }
    }
    private ClojureRuntime runtime;
    private String handlerName;
       
    private static final Logger log = Logger.getLogger( "org.immutant.web.servlet" );

    private static final long serialVersionUID = 1L;
}
