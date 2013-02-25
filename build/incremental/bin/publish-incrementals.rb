#!/usr/bin/env ruby

require 'rubygems'
$: << File.dirname( __FILE__ ) + '/../lib'
require 'dav'
require 'find'
require 'pathname'
require 'json'

class Publisher

  BASE_URL = 'https://repository-projectodd.forge.cloudbees.com/incremental/immutant'

  DIST_FILES = ['/../../dist/target/immutant-dist-slim.zip',
                '/../../dist/target/immutant-dist-full.zip',
                '/../../dist/target/immutant-dist-modules.zip',
                '/../../dist/target/build-metadata.json'].inject([]) do |acc, f|
    file = File.dirname(__FILE__) + f
    acc << file
    sha1 = file + ".sha1"
    acc << sha1 if File.exists?( sha1 )
    acc
  end
  
  attr_accessor :build_number

  def initialize(credentials_path, build_number)
    @build_number = build_number
    @dav          = DAV.new( credentials_path )
    @published_artifacts = []
  end

  def puts_r(result)
    puts "Result: #{result.first} - #{result.last}"
  end
  
  def build_base_url
    BASE_URL + "/#{build_number}"
  end

  def latest_base_url
    BASE_URL + "/LATEST"
  end

  def dav_mkdir_p(url)
    puts "mkdir #{url}"
    puts_r @dav.mkcol( url )
  end

  def dav_put(url, file, remember = true)
    puts "put #{url}"
    puts_r @dav.put( url, file )
    @published_artifacts << url if remember
  end

  def dav_rm_rf(url)
    puts_r @dav.delete( url )
  end

  def dav_remote_cp_r(src, dest)
    puts_r @dav.copy( src + '/', dest + '/', :infinity )
  end

  def dav_put_r(root_url, root_dir)
    Dir.chdir( root_dir ) do 
      Find.find( '.' ) do |entry|
        if ( entry == '.' )
          next
        end

        if ( File.directory?( entry ) )
          dav_mkdir_p( root_url + '/' + Pathname( entry ).cleanpath.to_s )
        else
          dav_put( root_url + '/' + Pathname( entry ).cleanpath.to_s, entry, false )
        end
      end
      @published_artifacts << root_url
    end
    
  end

  def publish_all()
    dav_mkdir_p( build_base_url )
    publish_distribution()
    verify_distribution()
    publish_documentation()
    copy_slim_to_bin() # TODO: remove me - see IMMUTANT-229
    publish_artifact_list()
    copy_to_latest()
    verify_distribution( :latest )
  end

  def copy_slim_to_bin()
    dest_url = build_base_url + "/immutant-dist-bin.zip"
    puts_r @dav.copy( build_base_url + "/immutant-dist-slim.zip",
                      dest_url, 1 )
    @published_artifacts << dest_url
    
    dest_url = build_base_url + "/immutant-dist-bin.zip.sha1"
    puts_r @dav.copy( build_base_url + "/immutant-dist-slim.zip.sha1",
                      dest_url, 1 )
    @published_artifacts << dest_url
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
    DIST_FILES.each do |file|
      dav_put( build_base_url + "/#{File.basename( file )}", file )
    end
  end

  def verify_distribution( latest = false )
    base_url = latest ? latest_base_url : build_base_url 
    DIST_FILES.each do |file|
      status, message = @dav.curl( "-I", base_url + "/#{File.basename( file )}" )
      raise Exception.new( "Release verification failed for " +
                           "#{File.basename( file )} - #{status} : #{message}" ) if status.to_i != 200
    end
  end

  def publish_artifact_list
    file = File.join( File.dirname( __FILE__ ), '..', 'target', 'published-artifacts.json' )
    File.open( file, 'w' ) { |f| f << @published_artifacts.to_json }
    dav_put( build_base_url + '/published-artifacts.json', file, false )
  end
end

Publisher.new( ARGV[0], ARGV[1] ).publish_all



