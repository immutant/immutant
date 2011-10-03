Requirements
------------
* Maven 3
* Configuration of the JBoss Maven repository in settings.xml


Dependencies
------------

(fn box) depends on polyglot: https://github.com/projectodd/jboss-polyglot

You'll need to pull and build it before building (fn box), and it will
stuff its artifacts into ~/.m2/


Building
--------

Install the project using the provided settings.xml:

    mvn -s support/settings.xml install

If you will be building the project often, you'll want to
create/modify your own ~/.m2/settings.xml file.

If you're a regular JBoss developer, see:

* http://community.jboss.org/wiki/MavenGettingStarted-Developers

Otherwise, see: 

* http://community.jboss.org/wiki/MavenGettingStarted-Users

Once your repositories are configured, simply type:

    mvn install


SLIME/SWANK Integration
-----------------

If you are using emacs, you can fire up swank via:
  
    mvn clojure:swank
    
Install slime and clojure-mode in emacs, and connect with:

    M-x slime-connect
    
If installing slime via el-get, you may need to modify the default 
recipe to not build and install docs. Here is the modified version:

    (:name slime
	       :description "Superior Lisp Interaction Mode for Emacs"
	       :type git
	       :module "slime"
	       :url "https://github.com/nablaone/slime.git"
	       :load-path ("." "contrib")
	       :compile ("."))
           
