(ns noir-app.views.welcome
  (:require [noir-app.views.common :as common]
            [noir.content.getting-started])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [korma.core :only [select]]
        [noir-app.models :only [authors]]))

(defpage "/welcome" []
  (common/layout
   [:p (str "Welcome to noir-app, " (-> (select authors) first :username))]))
