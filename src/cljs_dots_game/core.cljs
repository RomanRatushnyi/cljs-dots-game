(ns cljs-dots-game.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [cljs-dots-game.game :as game]
            [cljs-dots-game.types :as types]))

;; Состояние приложения
(defonce app-state (r/atom (game/init-game 10 10)))

;; Обработчик клика по клетке
(defn handle-cell-click [x y]
  (swap! app-state game/make-move x y))

;; Компонент клетки
(defn cell-component [x y cell-value]
  (let [class-name (cond
                    (= cell-value (:player1 types/player)) "cell player1"
                    (= cell-value (:player2 types/player)) "cell player2"
                    (= cell-value (:captured types/cell-state)) "cell captured"
                    :else "cell empty")]
    [:div {:class class-name
           :on-click #(handle-cell-click x y)}
     (cond
       (= cell-value (:player1 types/player)) "●"
       (= cell-value (:player2 types/player)) "●"
       (= cell-value (:captured types/cell-state)) "✕"
       :else "")]))

;; Компонент игровой доски
(defn board-component []
  (let [{:keys [board width height]} @app-state]
    [:div.board
     (for [y (range height)]
       [:div.row {:key y}
        (for [x (range width)]
          [cell-component x y (get-in board [y x]) {:key (str x "-" y)}])])]))

;; Компонент информации об игре
(defn game-info-component []
  (let [{:keys [current-player scores game-over winner]} @app-state
        score1 (get scores (:player1 types/player))
        score2 (get scores (:player2 types/player))]
    [:div.game-info
     [:h2 "Игра \"Точки\""]
     [:div.scores
      [:div (str "Игрок 1: " score1)]
      [:div (str "Игрок 2: " score2)]]
     (if game-over
       [:div.game-status
        (if winner
          (str "Победитель: Игрок " winner)
          "Ничья!")]
       [:div.game-status (str "Ход игрока: " current-player)])
     [:button {:on-click #(reset! app-state (game/init-game 10 10))}
      "Новая игра"]]))

;; Главный компонент
(defn app-component []
  [:div.app
   [game-info-component]
   [board-component]])

;; Инициализация приложения
(defn ^:export init! []
  (let [container (.getElementById js/document "app")
        root (rdom/create-root container)]
    (rdom/render root [app-component])))
