require 'fileutils'

ORG_VERSION = "org-7.8.03"

elisp_dir = File.join( File.dirname( __FILE__ ), '..', 'target', 'elisp' )
support_dir = File.join( File.dirname( __FILE__ ), '..', 'support' )

if !File.exists?( elisp_dir )
  FileUtils.mkdir_p( elisp_dir )
  Dir.chdir( elisp_dir ) do
    FileUtils.cp( File.join( support_dir, 'clojure-mode.el' ), elisp_dir )
    `unzip #{File.join( support_dir, 'org-mode.zip' )}`
    FileUtils.mv( ORG_VERSION, 'org-mode' )
  end
end



