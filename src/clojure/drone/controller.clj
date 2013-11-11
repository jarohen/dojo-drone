(ns drone.controller
  (:refer-clojure :exclude [read-string])
  (:require [chord.http-kit :refer [with-channel]]
            [clojure.core.async :refer [go-loop <!]]
            [clojure.tools.reader.edn :refer [read-string]]
            [clj-drone.core :as d]))

(defn process-command! [{:keys [subject args]}]
  (prn "Drone command" args)
  (try
    (prn "Result"
         (case subject
           :drone (apply d/drone args)
           :do-for (apply d/drone-do-for args)))
    (catch Exception e
      (prn "Error"))))

(defonce _init (d/drone-initialize))

(defn drone-socket [req]
  (with-channel req ws-ch
    (go-loop []
      (when-let [{:keys [message]} (<! ws-ch)]
        (process-command! (read-string message))
        (recur)))))


