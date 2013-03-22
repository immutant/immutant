;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns ^{:no-doc true} immutant.web.internal
  (:require
   [immutant.registry     :as registry]
   [immutant.util         :as util]
   [clojure.tools.logging :as log]))

(def ^{:dynamic true} ^javax.servlet.http.HttpServletRequest current-servlet-request nil)

(def ^{:private true} servlet-info (atom {}))

(defn get-servlet-info [name]
  (@servlet-info name))

(defn store-servlet-info! [name info]
  (swap! servlet-info assoc name info))

(defn remove-servlet-info! [name]
  (when-let [info (@servlet-info name)]
    (swap! servlet-info dissoc name)
    info))

(defn servlet-name [path]
  (str "immutant.ring." (util/app-name) "." path))

(defn normalize-subcontext-path 
  "normalize subcontext path so it matches Servlet Spec v2.3, section 11.2"
  [path]
  (loop [p path]
    (condp re-matches p
      #"^(/[^*]+)*/\*$" p                     ;; /foo/* || /*
      #"^(|[^/].*)"     (recur (str "/" p))   ;; prefix with /
      #".*?[^/]$"       (recur (str p "/"))   ;; add final /
      #"^(/[^*]+)*/$"   (recur (str p "*"))   ;; postfix with *
      (throw (IllegalArgumentException.
              (str "The context path \"" path "\" is invalid. It should be \"/\", \"/foo\", \"/foo/\", \"foo/\", or \"foo\""))))))

;; We have a race condition at deployment - the context isn't init'ed enough for
;; us to install servlets from clojure, and won't be until after the deployment
;; finishes. So we register a listener to do the work when the context becomes
;; available.
(defmacro when-context-available [context & body]
  `(if (.getAvailable ~context)
     (do
       ~@body)
     (do
       (.addPropertyChangeListener
        ~context
        (proxy [java.beans.PropertyChangeListener] []
          (propertyChange [evt#]
            (when (and (= "available" (.getPropertyName evt#))
                       (.getNewValue evt#)
                       (not (.getOldValue evt#)))
              ~@body))))
       nil)))

(defn virtual-hosts []
  (let [vh (or (:virtual-host (registry/get :config)) ["default-host"])]
    (if (coll? vh)
      vh
      [vh])))

(defn install-servlet [servlet sub-context-path]
  (let [context (registry/get "web-context")
        name (servlet-name sub-context-path)
        wrapper (.createWrapper context)
        mapper (-> (registry/get "jboss.web")
                   (.getService)
                   (.getMapper))
        servlet-class (.getName (class servlet))
        complete (promise)]
    (if (and
         (when-context-available
          context
          (doto wrapper
            (.setName name)
            (.setServletClass servlet-class)
            (.setServlet servlet)
            (.setEnabled true)
            (.setDynamic true)
            (.setAsyncSupported true)
            (.setLoadOnStartup -1))
          (doto context
            (.addChild wrapper)
            (.addServletMapping sub-context-path name))
          (doseq [host (virtual-hosts)]
            (.addWrapper mapper host (.getPath context) sub-context-path wrapper))
          (deliver complete true)
          true)
         (not (deref complete 5000 nil)))
      (log/error "Failed to install servlet for" sub-context-path)
      wrapper)))

(defn remove-servlet [sub-context-path wrapper]
  (let [context (registry/get "web-context")
        mapper (-> (registry/get "jboss.web")
                   (.getService)
                   (.getMapper))]
    (when-context-available
     context
     (.removeServletMapping context sub-context-path)
     (if wrapper (.removeChild context wrapper))
     (doseq [host (virtual-hosts)]
       (try
         (.removeWrapper mapper host (.getPath context) sub-context-path)
         (catch Exception _)))))
  nil)

(defn stop*
  "Deregisters the Ring handler attached to the given sub-context-path."
  [sub-context-path]
  (util/if-in-immutant
   (let [sub-context-path (normalize-subcontext-path sub-context-path)]
     (if-let [{:keys [wrapper destroy]} (remove-servlet-info! (servlet-name sub-context-path))]
       (do
         (log/info "Deregistering ring handler at sub-context path:" sub-context-path)
         (remove-servlet sub-context-path wrapper)
         (and destroy (destroy)))
       (log/warn "Attempted to deregister ring handler at sub-context path:" sub-context-path ", but none found")))
   (log/warn "web/stop called outside of Immutant, ignoring")))

(defn start*
  [sub-context-path servlet {:keys [init destroy] :as opts}]
  (util/if-in-immutant
   (let [sub-context-path (normalize-subcontext-path sub-context-path)
         servlet-name (servlet-name sub-context-path)]
     (when (get-servlet-info servlet-name)
       (stop* sub-context-path))
     (store-servlet-info!
      servlet-name
      {:wrapper (install-servlet servlet sub-context-path)
       :destroy destroy})
     (util/at-exit #(stop* sub-context-path))
     (and init (init)))
   (log/warn "web/start called outside of Immutant, ignoring")))

