(ns numerix.fields.markdown-render
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [$ $!]]
            [taoensso.timbre :as log]
            [numerix.lib.helpers :as h]
            [numerix.fields.dropdown :as dropdown]
            [numerix.site :as site]
            [clojure.string :as str]
            [re-com.core :refer-macros [handler-fn]]
            [taoensso.encore :as enc]
            [cuerdas.core :as cue]
            [numerix.api.project :as prj-api]
            [re-frame.core :as rf]
            [markdown.core :refer [md->html]]))

(defn markdown->html [md]
  [:div {:class "markdown-area"
         :dangerouslySetInnerHTML
                {:__html (md->html md)}}])
