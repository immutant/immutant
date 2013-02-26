#!/usr/bin/env ruby

require 'rubygems'
$: << File.dirname( __FILE__ ) + '/../../incremental/lib'
require 'dav'
require 'publish_tools'
require 'find'
require 'pathname'

class Publisher
  include PublishTools
  
  def initialize(credentials_path, base_url, version)
    @dav = DAV.new( credentials_path )
    @version = version
    @base_url = base_url
    @published_artifacts = []

    @dist_files = add_digests('/../../dist/target/immutant-dist-slim.zip',
                              '/../../dist/target/immutant-dist-full.zip',
                              '/../../dist/target/immutant-dist-modules.zip',
                              '/../../dist/target/build-metadata.json')
    
    @doc_files = add_digests('/../../../docs/target/immutant-docs-bin.zip')

  end

  def publish_all()
    dav_mkdir_p( dist_base_url )
    dav_mkdir_p( docs_base_url )
    publish_distribution()
    verify_distribution()
    copy_slim_to_bin() # TODO: remove me - see IMMUTANT-229
    publish_docs()
    verify_docs()
  end

  def dist_base_url
   "#{@base_url}/org/immutant/immutant-dist/#{@version}"
  end

  def docs_base_url
   "#{@base_url}/org/immutant/immutant-docs/#{@version}"
  end

  def add_version_to_filename(name)
    if name =~ /^(immutant-[^-.]*)(.*)$/
      "#{$1}-#{@version}#{$2}"
    else
      name
    end
  end
  
  def publish_distribution()
    @dist_files.each do |file|
      dav_put( dist_base_url + "/#{add_version_to_filename(File.basename( file ))}", file )
    end
  end

  def publish_docs()
    @doc_files.each do |file|
      dav_put( docs_base_url + "/#{add_version_to_filename(File.basename( file ))}", file )
    end
  end

  def copy_slim_to_bin()
    dest_url = dist_base_url + "/immutant-dist-#{@version}-bin.zip"
    puts_r @dav.copy( dist_base_url + "/immutant-dist-#{@version}-slim.zip",
                      dest_url )
    
    dest_url = dist_base_url + "/immutant-dist-#{@version}-bin.zip.sha1"
    puts_r @dav.copy( dist_base_url + "/immutant-dist-#{@version}-slim.zip.sha1",
                      dest_url )
  end

  def verify_distribution()
    @dist_files.each do |file|
      verify(dist_base_url, add_version_to_filename(File.basename(file)))
    end
  end

  def verify_docs()
    @doc_files.each do |file|
      verify(docs_base_url, add_version_to_filename(File.basename(file)))
    end
  end
end

Publisher.new( *ARGV ).publish_all



