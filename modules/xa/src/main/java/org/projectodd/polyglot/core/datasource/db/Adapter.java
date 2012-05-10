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

package org.projectodd.polyglot.core.datasource.db;

import java.util.Map;
import java.util.HashMap;

import org.jboss.jca.common.api.metadata.ds.DsSecurity;
import org.jboss.jca.common.api.metadata.ds.Validation;


public abstract class Adapter {

    static {
        new H2Adapter();
        new PostgresAdapter();
        new MySQLAdapter();
        new OracleAdapter();
    }

    public Adapter(String id, String requirePath, String driverClassName, String dataSourceClassName) {
        this.id = id;
        this.requirePath = requirePath;
        this.driverClassName = driverClassName;
        this.dataSourceClassName = dataSourceClassName;
        for (String name : this.getNames()) {
            adapters.put( name, this );
        }
    }

    public static Adapter find(Map<String,Object> spec) {
        Adapter result = find(spec.get("adapter").toString());
        return result==null ? find(spec.get("subprotocol").toString()) : result;
    }

    public static Adapter find(String name) {
        return adapters.get(name);
    }

    // Subclasses must override these

    public abstract Map<String,String> getPropertiesFor(Map<String,Object> config);
    public abstract String[] getNames();

    // Subclasses might override these

    public String getId() {
        return this.id;
    }
    
    public String getRequirePath() {
        return this.requirePath;
    }

    public String getDriverClassName() {
        return this.driverClassName;
    }

    public String getDataSourceClassName() {
        return this.dataSourceClassName;
    }
    
    public DsSecurity getSecurityFor(Map<String,Object> config) throws Exception {
        return null;
    }
    
    public Validation getValidationFor(Map<String,Object> config) throws Exception {
        return null;
    }

    private String id;
    private String requirePath;
    private String driverClassName;
    private String dataSourceClassName;

    private static Map<String,Adapter> adapters = new HashMap<String,Adapter>();

}
