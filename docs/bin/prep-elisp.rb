require 'fileutils'

ORG_VERSION = "org-7.8.03"
ORG_MODE = "http://orgmode.org/#{ORG_VERSION}.zip"
CLOJURE_MODE = "https://raw.github.com/jochu/clojure-mode/master/clojure-mode.el"

elisp_dir = File.join( File.dirname( __FILE__ ), '..', 'target', 'elisp' )
if !File.exists?( elisp_dir )
  puts elisp_dir
  FileUtils.mkdir_p( elisp_dir )
  Dir.chdir( elisp_dir ) do
    `wget #{CLOJURE_MODE}`
    `wget #{ORG_MODE}`
    `unzip #{ORG_VERSION}.zip`
    FileUtils.mv( ORG_VERSION, 'org-mode' )
  end
end



