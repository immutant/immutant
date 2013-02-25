# Copyright 2008-2011 Red Hat, Inc, and individual contributors.
# 
# This is free software; you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation; either version 2.1 of
# the License, or (at your option) any later version.
# 
# This software is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
# 
# You should have received a copy of the GNU Lesser General Public
# License along with this software; if not, write to the Free
# Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
# 02110-1301 USA, or see the FSF site: http://www.fsf.org.

require 'rexml/document'
require 'open3'

class DAV

  def initialize(credentials_path)
    load_credentials(credentials_path)
  end

  def load_credentials(credentials_path)
    text = File.read( credentials_path )
    doc = REXML::Document.new( text )
    @username = doc.get_elements( '//servers/server/username' ).first.text
    @password = doc.get_elements( '//servers/server/password' ).first.text
  end

  def mkcol(url)
    curl(
      '--request MKCOL',
      "--header 'Content-Type: text/xml; charset=\"utf-8\"'",
      url
    )
  end

  def put(url, file)
    curl(
      '--upload-file', 
      file,
      url
    )
  end

  def delete(url)
    curl(
      '--request DELETE',
      "--header 'Content-Type: text/xml; charset=\"utf-8\"'",
      url
    )
  end

  def copy(src, dest, depth = nil)
    cmd = ['--request COPY',
           "--header 'Destination: #{dest}'"]
    cmd << "--header 'Depth: #{depth}'" if depth
    cmd << src
    curl(*cmd)
  end

  def curl!(cmd_frag)
    cmd = "curl -v -s -u#{@username}:#{@password} #{cmd_frag}"
    puts "CMD: #{cmd_frag}"
    response = ''
    error    = ''
    Open3.popen3( cmd ) do |stdin, stdout, stderr|
      stdin.close
      stdout_thr = Thread.new(stdout) do |stream|
        while ( ! stream.eof? )
          response += stream.readline
        end
      end
      stderr_thr = Thread.new(stderr) do |stream|
        while ( ! stream.eof? )
          error += stream.readline
        end
      end
      stdout_thr.join
      stderr_thr.join
    end

    #puts error
    status_line = error.split( "\n" ).reverse.find{|e| e =~ /^< HTTP\/1\.[0-1]/}
    if status_line =~ /HTTP\/1\.[0-1] ([0-9][0-9][0-9]) (.*)$/ &&
        $1 != '100' # if the last status was a 100, then we crapped out before the final status
      status = $1
      message = $2.strip
    else
      status  = '500'
      message = 'Unknown'
    end
    [ status, message ]
  end

  def curl_with_retry(cmd, attempt = 5)
    result = curl!(cmd)
    
    if result.first =~ /^5/ &&
        attempt > 0
      puts "Curl failed with #{result.first} - #{result.last}, retrying (#{attempt})"
      result = curl_with_retry(cmd, attempt - 1)
    end
    result
  end
  
  def curl(*args)
    curl_with_retry(args.join(' '))    
  end
  
end
