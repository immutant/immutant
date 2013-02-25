puts 'building /target/build-metadata.json'
require 'java'
require 'rubygems'
require 'json'
require File.join( File.dirname( __FILE__ ),
                   "../../assembly/target/stage/immutant/jboss/modules/org/immutant/core/main/immutant-core-module.jar" )
require File.join( File.dirname( __FILE__ ),
                   "../../assembly/target/stage/immutant/jboss/modules/org/projectodd/polyglot/core/main/polyglot-core.jar" )

props = org.projectodd.polyglot.core.util.BuildInfo.new( java.lang.Thread.currentThread.getContextClassLoader,
                                                         "org/immutant/immutant.properties" )
immutant = props.getComponentInfo( 'Immutant' )

metadata = {}
metadata['build_revision'] = immutant['build.revision']
metadata['build_number'] = immutant['build.number']
metadata['build_time'] = Time.now.to_i
metadata['slim_dist_size'] = File.size( './target/immutant-dist-slim.zip' )
metadata['dist_size'] = metadata['slim_dist_size'] # TODO: remove me - see IMMUTANT-229
metadata['full_dist_size'] = File.size( './target/immutant-dist-full.zip' )
File.open('./target/build-metadata.json', 'w') do |f|
  f.write( metadata.to_json )
end
