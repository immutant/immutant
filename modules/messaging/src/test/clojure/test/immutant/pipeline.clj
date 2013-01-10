;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns test.immutant.pipeline
  (:use immutant.pipeline
        immutant.test.helpers
        clojure.test
        midje.sweet))

(background
 (immutant.util/app-name) => "app")

(deffact "step"
  (fact "it should attach the options as meta"
    (meta (step #() :foo :bar :ham :biscuit)) => {:foo :bar :ham :biscuit})
  
  (fact "it should preserve existing meta data"
    (meta (step (with-meta #() {:gravy :biscuit})
                :foo :bar :ham :biscuit)) => {:foo :bar :ham :biscuit :gravy :biscuit}))

(deffact "stop"
  (fact "should call messaging/stop with the pipeline from the metadata"
    (stop (with-meta #() {:pipeline "queue-name"})) => anything
    (provided
      (immutant.messaging/stop "queue-name") => anything))

  (fact "should call messaging/stop with any options"
    (stop (with-meta #() {:pipeline "queue-name"}) :force true) => anything
    (provided
      (immutant.messaging/stop "queue-name" :force true) => anything))

  (fact "should stop the attached listeners"
    (stop (with-meta #() {:pipeline "queue-name" :listeners [:a]})) => anything
    (provided
      (immutant.messaging/stop anything) => anything
      (immutant.messaging/unlisten :a) => anything)))

(deffact "pipeline"
  (facts "starting the queue"

    (fact "should pass a queue name based on the given name"
      (pipeline "my-pl") => anything
      (provided
        (immutant.messaging/start "queue.app.pipeline-my-pl") => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil))

    (fact "should pass a queue name based on the given name, even if it's a keyword"
      (pipeline :pl) => anything
      (provided
        (immutant.messaging/start "queue.app.pipeline-pl") => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil))
    
    (fact "should pass a options through to start"
      (pipeline "my-pl-with-options" :ham :biscuit) => anything
      (provided
        (immutant.messaging/start "queue.app.pipeline-my-pl-with-options"
                                  :ham :biscuit) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil)))

  (facts "the returned value"
    
    (fact "should have the pipeline queue as metadata"
      (-> "name" pipeline meta :pipeline) => "queue.app.pipeline-name"
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil))

    (fact "should have the listeners as metadata"
      (-> "ham" (pipeline #() #()) meta :listeners) => [:a :b :c]
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) =streams=> [:a :b :c]))
    
    (fact "should be a fn"
      (pipeline :foo) => fn?
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil))

    (fact "should publish to the pipeline queue when invoked"
      ((pipeline :bar #()) :ham) => anything
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil
        (immutant.messaging/publish "queue.app.pipeline-bar" :ham
                                    :properties {"step" "0"}
                                    :correlation-id anything) => anything))

    (fact "should publish to the pipeline queue with the correct step"
      ((pipeline :bart (step #() :name "onesies")) :ham) => anything
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil
        (immutant.messaging/publish anything :ham
                                    :properties {"step" "onesies"}
                                    :correlation-id anything) => anything))
        
    (fact "should raise if the named pl already exists"
      (pipeline "shamwow") => anything
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil)
      (pipeline "shamwow") => (throws IllegalArgumentException))
    
    (fact "should raise if given a non-existent step"
      ((pipeline "slapchop" (step #() :name :biscuit))
       nil :step :ham) => (throws IllegalArgumentException)
      (provided
        (immutant.messaging/start anything) => nil
        (#'immutant.pipeline/pipeline-listen anything anything anything) => nil))))




