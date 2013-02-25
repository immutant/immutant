module PublishTools
  def puts_r(result)
    puts "Result: #{result.first} - #{result.last}"
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

  def verify(base_url, file)
    status, message = @dav.curl( "-I", base_url + "/#{File.basename( file )}" )
    raise Exception.new( "Release verification failed for " +
                         "#{File.basename( file )} - #{status} : #{message}" ) if status.to_i != 200
  end

  def add_digests(*files)
    files.inject([]) do |acc, f|
      file = File.dirname(__FILE__) + f
      acc << file
      sha1 = file + ".sha1"
      acc << sha1 if File.exists?( sha1 )
      md5 = file + ".md5"
      acc << md5 if File.exists?( md5 )
      acc
    end
  end

end
