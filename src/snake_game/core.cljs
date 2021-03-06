(ns snake-game.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [register-handler register-sub subscribe dispatch dispatch-sync]]
            [goog.events :as events]))

(def key-code->move
  {38 [0 -1]
   40 [0 1]
   39 [1 0]
   37 [-1 0]})

(enable-console-print!)

(println "This text is printed from src/snake-game/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))



(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)


(def board [35 25])

(def snake {:direction [1 0]
            :body [[3 2] [2 2] [1 2] [0 2]]})

(defn rand-free-position [snake [x y]]
  (let [snake-positions-set (into #{} (:body snake))
        board-positions (for [x-pos (range x)
                              y-pos (range y)]
                          [x-pos y-pos])]
    (when-let [free-positions (seq (remove snake-positions-set board-positions))]
      (rand-nth free-positions))))

(def initial-state {:board board
                    :snake snake
                    :point (rand-free-position snake board)
                    :points 0
                    :game-running? true})

(register-handler
 :initialize
 (fn [db _]
   (merge db initial-state)))

(register-sub
 :board
 (fn [db _]
   (reaction (:board @db))))

(defn render-board
  []
  (let [board (subscribe [:board])
        snake (subscribe [:snake])
        point (subscribe [:point])]
    (fn []
      (let [[width height] @board
            snake-positions (into #{} @snake)
            current-point @point
            cells (for [y (range height)]
                    (into [:tr]
                          (for [x (range width)
                                :let [current-pos [x y]]]
                            (cond
                              (snake-positions current-pos) [:td.snake-on-cell]
                              (= current-pos current-point) [:td.point]
                              :else [:td.cell]))))]
        (into [:table.stage {:style {:height 377
                                     :width 527}}]
              cells)))))

(register-sub
 :snake
 (fn [db _]
   (reaction (:body (:snake @db)))))

(register-sub
 :point
 (fn
   [db _]
   (reaction (:point @db))))

(register-sub
 :points
 (fn
   [db _]
   (reaction (:points @db))))

(defn score
  []
  (let [points (subscribe [:points])]
    (fn []
      [:div.score (str "Score: " @points)])))

(register-sub
 :game-running?
 (fn
   [db _]
   (reaction (:game-running? @db))))




(defn move-snake
  [{:keys [direction body] :as snake}]
  (let [head-new-position (mapv + direction (first body))]
    (update-in snake [:body] #(into []
                                    (drop-last
                                     (cons head-new-position
                                           body))))))

(defn change-snake-direction [[new-x new-y] [x y]]
  (if (or (= x new-x)
          (= y new-y))
    [x y]
    [new-x new-y]))

(register-handler
 :change-direction
 (fn [db [_ new-direction]]
   (update-in db [:snake :direction] (partial change-snake-direction new-direction))))

(defn game-over
  []
  (let [game-state (subscribe [:game-running?])]
    (fn []
      (if @game-state
        [:div]
        [:div.overlay
         [:div.play {:on-click #(dispatch [:initialize])}
          [:h1 "↺"]]]))))

(defn snake-tail [coordinate-1 coordinate-2]
  (if (= coordinate-1 coordinate-2)
    coordinate-1
    (if (> coordinate-1 coordinate-2)
      (dec coordinate-2)
      (inc coordinate-1))))

(defn grow-snake
  [{:keys [body direction] :as snake}]
  (let [[[first-x first-y] [sec-x sec-y]] (take-last 2 body)
        x (snake-tail first-x sec-x)
        y (snake-tail first-y sec-y)]
    (update-in snake [:body] #(conj % [x y]))))

(defn process-move
  [{:keys [snake point board] :as db}]
  (if (= point (first (:body snake)))
    (-> db
        (update-in [:snake] grow-snake)
        (update-in [:points] inc)
        (assoc :point (rand-free-position snake board)))
    db))

(defn collisions
  [snake board]
  (let [{:keys [body direction]} snake
        [x y] board
        border-x #{x -1}
        border-y #{y -1}
        future-x (+ (first direction) (ffirst body))
        future-y (+ (second direction) (second (first body)))]
    (or (contains? border-y future-y)
        (contains? border-x future-x)
        (contains? (into #{} (rest body)) [future-x future-y]))))

(register-handler
 :next-state
 (fn [{:keys [snake board] :as db} _]
   (if (:game-running? db)
     (if (collisions snake board)
       (assoc-in db [:game-running?] false)
       (-> db
           (update :snake move-snake)
           (as-> after-move
               (process-move after-move))))
     db)))

(defn game
  []
  [:div
   [render-board]
   [score]
   [game-over]])

(defonce snake-moving
  (js/setInterval #(dispatch [:next-state]) 150))

(defonce key-handler
  (events/listen js/window "keydown"
                 (fn [e]
                   (let [key-code (.-keyCode e)]
                     (when (contains? key-code->move key-code)
                       (dispatch [:change-direction (key-code->move key-code)]))))))

(defn run []
  (dispatch-sync [:initialize])
  (reagent/render [game]
                  (js/document.getElementById "app")))


(run)
