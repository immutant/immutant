;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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

(ns immutant.init
  (:require [immutant.messaging :as msg]
            [immutant.web       :as web]))

(msg/start "/topic/gravy")
(msg/start (msg/as-topic "toddball"))

(let [p (promise)
      l (msg/listen "/topic/gravy" (fn [v] (deliver p v)))]
  (try
    @l
    (msg/publish "/topic/gravy" :success)
    (let [delivery (deref p 1000 :fail)]
      (if-not (= :success delivery)
        (throw (Exception. (str "Should have received :success, but got " delivery)))))
    (finally
     (msg/unlisten l))))

(msg/start "queue.198")
(msg/start "topic.198")

;;; Topic listeners are additive, not idempotent
@(msg/listen "topic.198" #(msg/publish "queue.198" (inc %)))
@(msg/listen "topic.198" #(msg/publish "queue.198" (dec %)))

(msg/start "queue.result")
(msg/start "topic.echo")

(let [responder (atom nil)]
  (web/start
   (fn [request]
     (if @responder
       (do
         (msg/unlisten @responder)
         (reset! responder nil))
       (reset! responder @(msg/listen "topic.echo" #(msg/publish "queue.result"
                                                                 (identity %)))))
     {:status 200
      :body ":success"})))
