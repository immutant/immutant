#!/usr/bin/env ruby

require 'rubygems'
$: << File.dirname( __FILE__ ) + '/../lib'
require 'dav'
require 'find'
require 'pathname'
require 'json'

class Publisher

  BASE_URL = 'https://repository-projectodd.forge.cloudbees.com/incremental/immutant'

  attr_accessor :build_number

  def initialize(credentials_path, build_number)
    @build_number = build_number
    @dav          = DAV.new( credentials_path )
    @published_artifacts = []
  end

  def build_base_url
    BASE_URL + "/#{build_number}"
  end

  def latest_base_url
    BASE_URL + "/LATEST"
  end

  def dav_mkdir_p(url)
    puts "mkdir #{url}"
    @dav.mkcol( url )
  end

  def dav_put(url, file, remember = true)
    puts "put #{url}"
    @dav.put( url, file )
    @published_artifacts << url if remember
  end

  def dav_rm_rf(url)
    @dav.delete( url )
  end

  def dav_remote_cp_r(src, dest)
    puts @dav.copy( src + '/', dest + '/', :infinity ).inspect
  end

  def dav_put_r(root_url, root_dir)
    Dir.chdir( root_dir ) do 
      Find.find( '.' ) do |entry|
        if ( entry == '.' )
          next
        end

        if ( File.directory?( entry ) )
          dav_mkdir_p( root_url + '/' + Pathname( entry ).cleanpath )
        else
          dav_put( root_url + '/' + Pathname( entry ).cleanpath, entry, false )
        end
      end
      @published_artifacts << root_url
    end
    
  end

  def publish_all()
    dav_mkdir_p( build_base_url )
    publish_distribution()
    publish_documentation()
    publish_artifact_list()
    copy_to_latest()
  end

  def copy_to_latest()
    dav_remote_cp_r( build_base_url, latest_base_url )
  end

  def html_docs_path()
    File.dirname(__FILE__) + '/../../../docs/target/html/'
  end

  def publish_documentation()
    dav_mkdir_p( build_base_url + '/html-docs' )
    dav_put_r( build_base_url + '/html-docs', html_docs_path )
  end

  def publish_distribution()
    [
     '/../../dist/target/immutant-dist-bin.zip',
     '/../../dist/target/immutant-dist-modules.zip',
     '/../../dist/target/build-metadata.json',
     '/../../assembly/target/stage/immutant/jboss/standalone/configuration/standalone.xml'
    ].each do |f|
      dav_put( build_base_url + "/#{File.basename( f )}", f )
      sha1 = f + "sha1"
      dav_put( build_base_url + "/#{File.basename( sha1 )}", sha1 ) if File.exists?( sha1 )
    end
  end

  def publish_artifact_list
    file = File.join( File.dirname( __FILE__ ), '..', 'target', 'published-artifacts.json' )
    File.open( file, 'w' ) { |f| f << @published_artifacts.to_json }
    dav_put( build_base_url + '/published-artifacts.json', file, false )
  end
end

Publisher.new( ARGV[0], ARGV[1] ).publish_all



