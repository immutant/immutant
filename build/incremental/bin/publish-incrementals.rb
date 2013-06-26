#!/usr/bin/env ruby

require 'rubygems'
$: << File.dirname( __FILE__ ) + '/../lib'
require 'dav'
require 'publish_tools'
require 'find'
require 'pathname'
require 'json'

class Publisher
  include PublishTools

  BASE_URL = 'https://repository-projectodd.forge.cloudbees.com/incremental/immutant'
  
  attr_accessor :build_number

  def initialize(credentials_path, build_number)
    @build_number = build_number
    @dav          = DAV.new( credentials_path )
    @published_artifacts = []

    @dist_files = add_digests('/../../dist/target/immutant-dist-slim.zip',
                              '/../../dist/target/immutant-dist-full.zip',
                              '/../../dist/target/immutant-dist-modules.zip',
                              '/../../dist/target/build-metadata.json')
  end
  
  def publish_all()
    dav_mkdir_p( build_base_url )
    publish_distribution()
    verify_distribution()
    publish_documentation()
    publish_artifact_list()
    copy_to_latest()
    verify_distribution( :latest )
  end

  def build_base_url
    BASE_URL + "/#{build_number}"
  end

  def latest_base_url
    BASE_URL + "/LATEST"
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
    @dist_files.each do |file|
      dav_put( build_base_url + "/#{File.basename( file )}", file )
    end
  end

  def verify_distribution( latest = false )
    base_url = latest ? latest_base_url : build_base_url 
    @dist_files.each do |file|
      verify( base_url, File.basename(file) )
    end
  end

  def publish_artifact_list
    file = File.join( File.dirname( __FILE__ ), '..', 'target', 'published-artifacts.json' )
    File.open( file, 'w' ) { |f| f << @published_artifacts.to_json }
    dav_put( build_base_url + '/published-artifacts.json', file, false )
  end
end

Publisher.new( ARGV[0], ARGV[1] ).publish_all



