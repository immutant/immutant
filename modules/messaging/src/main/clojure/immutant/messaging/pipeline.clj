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

(ns immutant.messaging.pipeline
  (:use [immutant.util :only (mapply)]
        immutant.messaging)
  (:require [clojure.tools.logging :as log]))

(defn- error-handler-fn [f {:keys [error-handler]}]
  (if error-handler
    (fn [m]
      (try
        (f m)
        (catch Exception e
          (error-handler e m))))
    f))

(defn- pipeline-listen [pl opts step-count cur-step f]
  (let [opts (-> opts
                 (merge (meta f))
                 (assoc :selector
                   (if (= cur-step 0)
                     (str "step is null")
                     (str "step = " cur-step))))
        wrapped-f (bound-fn*
                   (error-handler-fn f opts))]
    (mapply listen
            pl
            (if (= cur-step (dec step-count))
              wrapped-f
              #(publish pl (wrapped-f %)
                        :properties {"step" (inc cur-step)}))
            opts)))

(def ^{:dynamic true} *pipeline* nil)

(defn pipeline [name & args]
  (let [{:keys [result-destination] :as opts} (drop-while fn? args)
        fns (take-while fn? args)
        fns (if result-destination
              (conj (vec fns) #(publish result-destination %))
              fns)
        pl (as-queue (str "queue.pipeline-" name))]
    (when result-destination
      (start result-destination))
    (mapply start pl opts)
    (binding [*pipeline* pl]
      (doall
       (map-indexed (partial pipeline-listen pl opts (count fns)) fns)))
    pl))

(defn step [f & {:as opts}]
  (with-meta f opts))
