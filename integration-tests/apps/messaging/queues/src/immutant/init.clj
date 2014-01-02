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
            [immutant.web       :as web]
            [immutant.util      :as util]))

(msg/start "/queue/ham")
(msg/start ".queue.biscuit")
(msg/listen ".queue.biscuit" #(msg/publish "/queue/ham" (.toUpperCase %)))

(msg/start "/queuebam")
(msg/start "queue/hiscuit")

(msg/start "/queue/loader")
(msg/start "/queue/loader-result")
(msg/listen "/queue/loader"
            (fn [_]
              (msg/publish "/queue/loader-result"
                           (-> (Thread/currentThread)
                               .getContextClassLoader
                               .toString))))

(msg/start "queue.listen-id.request" :durable false)
(msg/start "queue.listen-id.response" :durable false)

(msg/listen "queue.listen-id.request"
            (fn [_]
              (future
                @(msg/listen "queue.listen-id.request"
                             (fn [_] (msg/publish "queue.listen-id.response" :new-listener)))
                (msg/publish "queue.listen-id.response" :release))
              (msg/publish "queue.listen-id.response" :old-listener)))

(msg/start (msg/as-queue "oddball"))
(msg/start (msg/as-queue "addboll"))
(msg/start (msg/as-queue "odd-response"))

(msg/listen (msg/as-queue "oddball")
            #(msg/publish "/queue/ham" (.toLowerCase %)))

(msg/start "queue.echo")

(let [responder (atom nil)]
  (web/start
   (fn [request]
     (if @responder
       (do
         (msg/unlisten @responder)
         (reset! responder nil))
       (reset! responder (msg/respond "queue.echo" identity)))
     {:status 200
      :body ":success"})))

(msg/start "queue.reconfigurable")
(msg/start "queue.not-reconfigurable")
(when (re-find #"^/q\d" (util/context-path))
  (Thread/sleep 2000)
  (msg/stop "queue.reconfigurable"))
