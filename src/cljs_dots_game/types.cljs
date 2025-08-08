(ns cljs-dots-game.types)

;; Константы для состояний клеток
(def cell-state
  {:empty 0
   :dot 1
   :captured 3})

;; Константы для игроков
(def player
  {:player1 1
   :player2 2})

;; Начальное состояние игры
(def initial-game-state
  {:board []
   :current-player (:player1 player)
   :scores {(:player1 player) 0
            (:player2 player) 0}
   :game-over false
   :winner nil
   :captured-squares #{}
   :width 10
   :height 10})

;; Позиция на доске
(defn position [x y]
  {:x x :y y})

;; Проверка валидности позиции
(defn valid-position? [pos width height]
  (and (>= (:x pos) 0)
       (< (:x pos) width)
       (>= (:y pos) 0)
       (< (:y pos) height)))

;; Получение соседних позиций
(defn get-neighbors [pos]
  (let [{:keys [x y]} pos]
    [(position (dec x) y)
     (position (inc x) y)
     (position x (dec y))
     (position x (inc y))]))

;; Получение значения клетки
(defn get-cell [board pos]
  (get-in board [(:y pos) (:x pos)]))

;; Установка значения клетки
(defn set-cell [board pos value]
  (assoc-in board [(:y pos) (:x pos)] value))
