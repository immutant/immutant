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

package org.immutant.core;

import java.io.IOException;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.projectodd.polyglot.core.ProjectInfo;

/**
 * Primary marker and build/version information provider.
 * 
 * @author Toby Crawley
 */
public class Immutant extends ProjectInfo implements ImmutantMBean, Service<Immutant> {
    
    public static final String ARCHIVE_SUFFIX = ".ima";
    public static final String DESCRIPTOR_SUFFIX = ".clj";
    
    /**
     * Construct.
     * 
     * @throws IOException
     *             if an error occurs while reading the underlying properties
     *             file.
     */
    public Immutant() throws IOException {
        super( "Immutant", "org/immutant/immutant.properties" );
    }

    public String getClojureVersion() {
        return getComponentValue( "Clojure", "version" );
    }
    
    @Override
    public Immutant getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    public String getVersionWithCodeName() {
        String codeName = getComponentValue("Immutant", "codename");
        if (codeName == null || codeName.trim().isEmpty()) {
            return getVersion();
        } else {
            return getVersion() + " (" + codeName + ")";
        }
    }

    public void printVersionInfo(Logger log) {
        log.info( "Welcome to Immutant AS - http://immutant.org/" );
        log.info( formatOutput( "version", getVersionWithCodeName() ) );
        String buildNo = getBuildNumber();
        if (buildNo != null && !buildNo.trim().equals( "" )) {
            log.info( formatOutput( "build", getBuildNumber() ) );
        } else if (getVersion().contains( "SNAPSHOT" )) {
            log.info( formatOutput( "build", "development (" + getBuildUser() + ")" ) );
        } else {
            log.info( formatOutput( "build", "official" ) );
        }
        log.info( formatOutput( "revision", getRevision() ) );

        List<String> otherCompoments = getBuildInfo().getComponentNames();
        otherCompoments.remove( "Immutant" );
        log.info( "  built with:" );
        for (String name : otherCompoments) {
            String version = getBuildInfo().get( name, "version" );
            if (version != null) {
                log.info( formatOutput( "  " + name, version ) );
            }
        }

    }

    @Override
    public void stop(StopContext context) {

    }

}
