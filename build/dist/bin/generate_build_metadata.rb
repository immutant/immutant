puts 'building /target/build-metadata.json'
require 'java'
require 'rubygems'
require 'json'
require File.join( File.dirname( __FILE__ ), '../../../modules/core/target/immutant-core-module.jar' )
require File.join( File.dirname( __FILE__ ), '../../../modules/core/target/immutant-core-module-module/polyglot-core.jar' )

props = org.projectodd.polyglot.core.util.BuildInfo.new( "org/immutant/immutant.properties" )
immutant = props.getComponentInfo( 'Immutant' )

metadata = {}
metadata['build_revision'] = immutant['build.revision']
metadata['build_number'] = immutant['build.number']
metadata['build_time'] = Time.now.to_i
dist_file = './target/immutant-dist-bin.zip'
metadata['dist_size'] = File.size( dist_file )
File.open('./target/build-metadata.json', 'w') do |f|
  f.write( metadata.to_json )
end
