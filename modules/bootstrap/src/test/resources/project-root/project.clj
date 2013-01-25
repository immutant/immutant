(defproject a-project "24.32.11"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]]
  :ham :biscuit
  :egg :sandwich
  :immutant {:ham "basket"
             :biscuit "gravy"
             :init some.namespace/init
             :resolve-dependencies false
             :lein-profiles [:cheese]}
  :profiles {:gravy {:ham :not-bacon}
             :cheese {:egg :biscuit}})
