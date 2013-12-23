(ns immutant.init
  (:require [immutant.web             :as web]
            [immutant.web.session     :as immutant-session]
            [ring.middleware.session  :as ring-session]
            [ring.util.response       :as ring-util]))

(defn counter [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (-> (ring-util/response (pr-str count))
        (assoc :session session))))
(web/start "/counter"
 (ring-session/wrap-session
  #'counter
  {:store (immutant-session/servlet-store)}))
