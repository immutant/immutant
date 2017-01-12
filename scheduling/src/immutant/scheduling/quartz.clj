;; Copyright 2014-2017 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.scheduling.quartz
    "Utilities specific to [Quartz](http://quartz-scheduler.org/)."
    (:require [immutant.scheduling.internal :as int])
  (:import org.projectodd.wunderboss.scheduling.QuartzScheduling))

(defn quartz-scheduler
  "Returns the internal Quartz scheduler instance for the given options.

  `opts` should be the same scheduler options passed
  to [[immutant.scheduling/schedule]] (currently just :num-threads)."
  [opts]
  (let [^QuartzScheduling quartz-scheduler (-> (merge int/create-defaults opts)
                                             int/scheduler
                                             (doto .start))]
    (.scheduler quartz-scheduler)))
