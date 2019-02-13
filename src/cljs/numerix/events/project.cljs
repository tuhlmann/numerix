(ns numerix.events.project
  (:require [taoensso.timbre :as log]
            [robur.events :refer [emit subscribe]]
            [numerix.views.common-controls :as ctrl]
            [numerix.events.user]
            [numerix.state :as s]
            [numerix.ws :as socket]
            [numerix.api.project :as project-api]
            [numerix.lib.helpers :as h]
            [numerix.lib.datatypes :as d]
            [re-frame.core :as rf]))

;;; EVENTS TRIGGERED BY THE VIEW

