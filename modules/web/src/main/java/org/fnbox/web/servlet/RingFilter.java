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

package org.fnbox.web.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.fnbox.core.ClojureRuntime;
import org.jboss.logging.Logger;
import org.projectodd.polyglot.web.servlet.HttpServletResponseCapture;

public class RingFilter implements Filter {
    public static final String RING_APP_DEPLOYMENT_INIT_PARAM = "fnbox.ring.app.deployment.name";
    public static final String CLOJURE_SCRIPT_NAME = "clojure.script.name";
    public static final String CLOJURE_NAMESPACE = "clojure.namespace";
    public static final String CLOJURE_APP_FUNCTION_NAME = "clojure.app.function.name";
    public static final String CLOJURE_RUNTIME = "clojure.runtime";
    
    public void init(FilterConfig filterConfig) throws ServletException {
        this.runtime = (ClojureRuntime)filterConfig.getServletContext().getAttribute( CLOJURE_RUNTIME );
        this.scriptName = (String)filterConfig.getServletContext().getAttribute( CLOJURE_SCRIPT_NAME );
        this.namespace = (String)filterConfig.getServletContext().getAttribute( CLOJURE_NAMESPACE );
        this.appFunctionName = (String)filterConfig.getServletContext().getAttribute( CLOJURE_APP_FUNCTION_NAME );
        
//        ServiceRegistry registry = (ServiceRegistry) filterConfig.getServletContext().getAttribute( "service.registry" );
//
//        ServiceName componentResolverServiceName = (ServiceName) filterConfig.getServletContext().getAttribute( "component.resolver.service-name" );
//        this.componentResolver = (ComponentResolver) registry.getService( componentResolverServiceName ).getValue();
//        if (this.componentResolver == null) {
//            throw new ServletException( "Unable to obtain Rack component resolver: " + componentResolverServiceName );
//        }
//
//        ServiceName runtimePoolServiceName = (ServiceName) filterConfig.getServletContext().getAttribute( "runtime.pool.service-name" );
//        this.runtimePool = (RubyRuntimePool) registry.getService( runtimePoolServiceName ).getValue();
//
//        if (this.runtimePool == null) {
//            throw new ServletException( "Unable to obtain runtime pool: " + runtimePoolServiceName );
//        }
//
//        this.servletContext = filterConfig.getServletContext();
    }

    public void destroy() {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doFilter( (HttpServletRequest) request, (HttpServletResponse) response, chain );
        }
    }

    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (((request.getPathInfo() == null) || (request.getPathInfo().equals( "/" ))) && !(request.getRequestURI().endsWith( "/" ))) {
            String redirectUri = request.getRequestURI() + "/";
            String queryString = request.getQueryString();
            if (queryString != null) {
                redirectUri = redirectUri + "?" + queryString;
            }
            redirectUri = response.encodeRedirectURL( redirectUri );
            response.sendRedirect( redirectUri );
            return;
        }

        HttpServletResponseCapture responseCapture = new HttpServletResponseCapture( response );
        try {
            chain.doFilter( request, responseCapture );
            if (responseCapture.isError()) {
                response.reset();
            } else {
                return;
            }
        } catch (ServletException e) {
            log.error( "Error performing request", e );
        }
        doRing( request, response );
    }

    protected void doRing(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
       try { 
           this.runtime.load( this.scriptName );
           this.runtime.load( "fnbox/web" );
           this.runtime.invoke( "fnbox.web", "handle-request", this.namespace, this.appFunctionName, request, response );
       } catch (Exception e) {
            log.error( "Error invoking Ring filter", e );
            throw new ServletException( e );
        }
    }

    private ClojureRuntime runtime;
    private String scriptName;
    private String namespace;
    private String appFunctionName;
    
    private static final Logger log = Logger.getLogger( RingFilter.class );

   
}
