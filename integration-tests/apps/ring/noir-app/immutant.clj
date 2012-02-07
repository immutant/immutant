(ns noir-app.init
  ;(:use noir-app.core)
  (:require [immutant.messaging :as messaging]
            [immutant.web :as web]))

;; This file will be loaded when the application is deployed to Immutant, and
;; can be used to start services your app needs. Examples:

;; Web endpoints need a context-path and ring handler function. The context
;; path given here is a sub-path to the global context-path for the app
;; if any.

; (web/start "/" my-ring-handler)
; (web/start "/foo" a-different-ring-handler)

;; Web endpoints can be stopped from anywhere in your code with:
;; (web/stop "/")
;; This is optional - any endpoints you leave up will be stopped when your
;; app is undeployed.

;; Messaging allows for starting (and stopping) destinations (queues & topics)
;; and listening for messages on a destination.

; (messaging/start "/queue/a-queue")
; (messaging/listen "/queue/a-queue" #(println "received: " %))

;; Destinations can be stopped with:
;; (messaging/stop "/queue/a-queue")
;; This is optional - any destinations you leave up will be stopped when your
;; app is undeployed.

