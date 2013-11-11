(ns drone.cljs.home
  (:require [dommy.core :as d]
            [cljs.core.async :as a]
            [clojure.string :as s]
            [chord.client :refer [ws-ch]]
            [cljs.reader :refer [read-string]]
            [goog.events.KeyCodes :as kc])
  (:require-macros [dommy.macros :refer [node sel1]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn drone-node [bind-command! bind-take-off! bind-land!]
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
                (bind-command! $input))]]))]
     (doto (node [:button.btn "Take off"])
       bind-take-off!)
      
     (doto (node [:button.btn {:style {:margin-left "2em"}} "Land"])
       bind-land!)]]))

(defn command-binder [!input command-ch]
  (fn bind-command! [$button $input]
    (letfn [(focus! []
              (go
               (a/<! (a/timeout 200))
               (.focus $input)))]
      (add-watch !input ::command
                 (fn [_ _ _ new-input]
                   (d/set-value! $input new-input)))
      (d/listen! $button :click
                 (fn [e]
                   (let [input (d/value $input)]
                     (when-not (s/blank? input)
                       (a/put! command-ch {:command input
                                           :subject :do-for})))
                   (.preventDefault e))))))

(defn bind-take-off! [command-ch]
  (fn [$button]
    (d/listen! $button :click
               (fn [e]
                 (js/console.log "Here!")
                 (a/put! command-ch {:command ":take-off"
                                     :subject :drone})
                 (.preventDefault e)))))

(defn bind-land! [command-ch]
  (fn [$button]
    (d/listen! $button :click
               (fn [e]
                 (a/put! command-ch {:command ":land"
                                     :subject :drone})
                 (.preventDefault e)))))

(defn parse-command [command subject]
  (-> command
      (s/split #"\s+")
      (->> (mapv read-string)
           (hash-map :args))
      (assoc :subject subject)
      pr-str))

(def ws-url
  (let [loc js/location]
    (str "ws://" (.-host loc) "/drone")))

(defn process-commands! [!input command-ch]
  (go
   (let [ws (a/<! (ws-ch ws-url))]
     (go-loop []
       (when-let [{:keys [command subject]} (a/<! command-ch)]
         (js/console.log (parse-command command subject))
         (a/>! ws (parse-command command subject))
         (reset! !input nil)
         (recur))))))

(def keycode->command
  {kc/W ":tilt-front 0.5"
   kc/A ":tilt-left 0.5"
   kc/S ":tilt-back 0.5"
   kc/D ":tilt-right 0.5"
   kc/UP ":up 0.3"
   kc/DOWN ":down 0.3"
   kc/LEFT ":spin-left 0.5"
   kc/RIGHT ":spin-right 0.5"})

(defn bind-keys! [command-ch]
  (d/listen! js/document :keyup
            (fn [e]
               (when-let [command (keycode->command (.-keyCode e))]
                 (a/put! command-ch {:command (str "1 " command)
                                     :subject :do-for})
                 (.preventDefault e)))))

(defn watch-hash! [!hash]
  (add-watch !hash :home-page
             (fn [_ _ _ hash]
               (when (= "#/" hash)
                 (let [command-ch (a/chan)
                       !input (atom nil)]
                   (process-commands! !input command-ch)
                   (bind-keys! command-ch)
                   (d/replace-contents! (sel1 :#content)
                                        (drone-node (command-binder !input command-ch)
                                                    (bind-take-off! command-ch)
                                                    (bind-land! command-ch))))))))
