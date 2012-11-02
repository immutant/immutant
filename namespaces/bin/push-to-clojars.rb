#!/usr/bin/env ruby

Dir["target/*"].each do |dir|
  if File.directory?(dir) 
    puts "Processing #{dir}..."
    Dir.chdir(dir) do
      if File.exists?("project.clj")
        cmd = "scp pom.xml *.jar clojars@clojars.org:"
        puts "-> #{cmd}"
        %x{#{cmd}}
      else
          puts "-> No project.clj found in #{dir} - skipping"
      end
    end
  end
end



