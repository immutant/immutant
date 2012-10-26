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

(ns immutant.messaging.stomp
  (:require [immutant.messaging :as msg]
            [immutant.registry :as r])
  (:import [org.projectodd.stilts.stomp DefaultHeaders StompMessages]))

(defn wrap-session
  [handler]
  handler)

(defn wrap-error [handler]
  handler
  )

(defn wrap-message [handler]
  handler)

(defn add-message [request message]
  (if (= :message (:op request))
    (assoc request :message message :headers {})
    request))

(defn add-conduit [request conduit]
  (if (contains? #{:subscribe :unsubscribe} (:op request))
    (assoc request
      :conduit conduit
      :destination (.getDestination conduit))
    request))

;; {:op :message                      :session session :message message-obj? :headers ?}
;; {:op :subscribe   :conduit conduit :session session :destination ? :params ?}
;; {:op :unsubscribe :conduit conduit :session session :destination ? :params ?}

(defn setup-handler [f]
  (fn [op session-obj payload]
    (let [op (keyword op)
          request (-> {:op op
                       :session session-obj
                       }
                      (add-message payload)
                      (add-conduit payload)
                      ;(add-params "foo")
                      )]
      ((-> f
           wrap-error
           wrap-message
           wrap-session) request))))

(defn start
  "Starts a stomp endpoint and registers the listeners."
  [route handler]
  (let [stompletizer (r/fetch "stompletizer")]
    (.createStomplet stompletizer route (setup-handler handler))))

(defn stop
  "Stops a stomp endpoint."
  [route])

(defn send-message
  "Sends a message to a conduit"
  [conduit message & {:as headers}]
  (.send conduit
         (StompMessages/createStompMessage
          (.getDestination conduit)
          (doto (DefaultHeaders.)
            (.put "subscription" (.getSubscriptionId conduit)))
          message)))

(defn send-error
  "Signifies an error to the conduit"
  [conduit reason & [details]]
  (.send conduit
         (StompMessages/createStompErrorMessage
          reason
          details)))

(defn bridge
  "Attaches a conduit as a listener to a messaging destination"
  [conduit destination & {:keys [selector]}]
  (msg/listen destination
              (partial send-message conduit)))

(defn endpoint
  "Returns the url for connecting to the stomp port: ws://some-host:8675"
  [])
