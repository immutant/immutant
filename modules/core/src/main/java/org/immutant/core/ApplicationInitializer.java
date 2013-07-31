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

package org.immutant.core;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.AsyncService;
import org.projectodd.shimdandy.ClojureRuntimeShim;

public class ApplicationInitializer<Void> extends AsyncService<Void> {

    public ApplicationInitializer(String fullAppConfig, 
                                  String leinProject, 
                                  ClojureMetaData metaData) {
        this.fullAppConfig = fullAppConfig;
        this.leinProject = leinProject;
        this.metaData = metaData;
    }

    @Override
    public void startAsync(StartContext context) throws Exception {
        ClojureRuntimeShim runtime = this.clojureRuntimeInjector.getValue();
        Timer t = new Timer("setting app config");
        runtime.invoke("immutant.runtime/set-app-config", 
                       this.fullAppConfig, 
                       this.leinProject);
        t.done();
        t = new Timer("initializing app");
        runtime.invoke("immutant.runtime/initialize", 
                       this.metaData.getInitFunction(), 
                       this.metaData.getConfig());
        t.done();
    }

    @Override
    public Void getValue() {
        return null;
    }
    
    @Override
    public synchronized void stop(StopContext context) {
    }

    public Injector<ClojureRuntimeShim> getClojureRuntimeInjector() {
        return this.clojureRuntimeInjector;
    }
    
    private String fullAppConfig;
    private String leinProject;
    private ClojureMetaData metaData;

    private InjectedValue<ClojureRuntimeShim> clojureRuntimeInjector = new InjectedValue<ClojureRuntimeShim>();

}
