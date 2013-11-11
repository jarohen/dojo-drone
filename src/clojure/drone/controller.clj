(ns drone.controller
  (:refer-clojure :exclude [read-string])
  (:require [chord.http-kit :refer [with-channel]]
            [clojure.core.async :refer [go-loop <!]]
            [clojure.tools.reader.edn :refer [read-string]]
            [clj-drone.core :as d]))

(defn process-command! [{:keys [args]}]
  (prn "Drone command" args)
  (try
    (prn "Result" (apply d/drone args))
    (catch Exception e
      (prn "Error"))))

(defonce _init (d/drone-initialize))

(defn drone-socket [req]
  (with-channel req ws-ch
    (go-loop []
      (when-let [{:keys [message]} (<! ws-ch)]
        (process-command! (read-string message))
        (recur)))))


