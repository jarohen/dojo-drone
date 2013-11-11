(ns drone.cljs.home
  (:require [dommy.core :as d]
            [cljs.core.async :as a]
            [clojure.string :as s]
            [chord.client :refer [ws-ch]]
            [cljs.reader :refer [read-string]])
  (:require-macros [dommy.macros :refer [node sel1]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn drone-node [bind-command!]
  (node
   [:div
    [:h2 "Drone command:"]
    [:div {:style {:margin-top "2em"}}
     [:form 
      (let [$input (node [:input {:type "text" :style {:width "25em"}}])]
        (node
         [:div
          [:p $input]
          [:p (doto (node [:button.btn.btn-default "Execute!"])
                (bind-command! $input))]]))]]]))

(defn command-binder [!input command-ch]
  (fn bind-command! [$button $input]
    (letfn [(focus! []
              (go
               (a/<! (a/timeout 200))
               (.focus $input)))]
      (focus!)
      (add-watch !input ::command
                 (fn [_ _ _ new-input]
                   (d/set-value! $input new-input)
                   (focus!)))
      (d/listen! $button :click
                 (fn [e]
                   (let [input (d/value $input)]
                     (when-not (s/blank? input)
                       (a/put! command-ch {:command input})))
                   (.preventDefault e))))))

(defn parse-command [command]
  (-> command
      (s/split #"\s+")
      (->> (mapv read-string)
           (hash-map :args))
      pr-str))

(def ws-url
  (let [loc js/location]
    (str "ws://" (.-host loc) "/drone")))

(defn process-commands! [!input command-ch]
  (go
   (let [ws (a/<! (ws-ch ws-url))]
     (go-loop []
       (when-let [{:keys [command]} (a/<! command-ch)]
         (a/>! ws (parse-command command))
         (reset! !input nil)
         (recur))))))

(defn watch-hash! [!hash]
  (add-watch !hash :home-page
             (fn [_ _ _ hash]
               (when (= "#/" hash)
                 (let [command-ch (a/chan)
                       !input (atom nil)]
                   (process-commands! !input command-ch)
                   (d/replace-contents! (sel1 :#content)
                                        (drone-node (command-binder !input command-ch))))))))
