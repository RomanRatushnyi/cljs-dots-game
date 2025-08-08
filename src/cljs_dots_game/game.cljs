(ns cljs-dots-game.game
  (:require [cljs-dots-game.types :as types]))

;; Создание пустой доски
(defn create-board [width height]
  (vec (repeat height (vec (repeat width (:empty types/cell-state))))))

;; Инициализация игры
(defn init-game [width height]
  (assoc types/initial-game-state
         :board (create-board width height)
         :width width
         :height height))

;; Проверка валидности хода
(defn valid-move? [game-state x y]
  (let [{:keys [board width height game-over]} game-state
        pos (types/position x y)]
    (and (not game-over)
         (types/valid-position? pos width height)
         (= (types/get-cell board pos) (:empty types/cell-state)))))

;; Поиск группы соединенных точек одного игрока
(defn find-group [board pos player width height visited]
  (if (contains? visited pos)
    #{}
    (let [cell-value (types/get-cell board pos)]
      (if (= cell-value player)
        (let [new-visited (conj visited pos)
              neighbors (filter #(types/valid-position? % width height)
                               (types/get-neighbors pos))
              group-from-neighbors (reduce (fn [acc neighbor]
                                            (into acc (find-group board neighbor player width height new-visited)))
                                          #{pos}
                                          neighbors)]
          group-from-neighbors)
        #{}))))

;; Проверка окружения группы
(defn group-surrounded? [board group current-player width height]
  (let [border-cells (set (mapcat (fn [pos]
                                   (filter #(types/valid-position? % width height)
                                          (types/get-neighbors pos)))
                                 group))
        external-border (filter #(not (contains? group %)) border-cells)]
    (every? #(= (types/get-cell board %) current-player) external-border)))

;; Поиск захваченных точек противника
(defn find-captured-enemy-points [board current-player width height]
  (let [enemy-player (if (= current-player (:player1 types/player))
                      (:player2 types/player)
                      (:player1 types/player))
        all-positions (for [y (range height)
                           x (range width)]
                       (types/position x y))
        enemy-positions (filter #(= (types/get-cell board %) enemy-player) all-positions)
        visited (atom #{})
        captured-groups (atom [])]

    (doseq [pos enemy-positions]
      (when (not (contains? @visited pos))
        (let [group (find-group board pos enemy-player width height @visited)]
          (when (not (empty? group))
            (swap! visited into group)
            (when (group-surrounded? board group current-player width height)
              (swap! captured-groups conj group))))))

    (apply into #{} @captured-groups)))

;; Захват точек
(defn capture-points [board points]
  (reduce (fn [new-board pos]
            (types/set-cell new-board pos (:captured types/cell-state)))
          board
          points))

;; Переключение игрока
(defn switch-player [current-player]
  (if (= current-player (:player1 types/player))
    (:player2 types/player)
    (:player1 types/player)))

;; Проверка окончания игры
(defn game-ended? [board width height]
  (let [total-cells (* width height)
        empty-cells (count (filter #(= % (:empty types/cell-state))
                                  (flatten board)))]
    (< empty-cells (* total-cells 0.1)))) ; Игра заканчивается когда меньше 10% клеток пустые

;; Определение победителя
(defn determine-winner [scores]
  (let [score1 (get scores (:player1 types/player))
        score2 (get scores (:player2 types/player))]
    (cond
      (> score1 score2) (:player1 types/player)
      (> score2 score1) (:player2 types/player)
      :else nil))) ; Ничья

;; Основная функция хода
(defn make-move [game-state x y]
  (if (not (valid-move? game-state x y))
    game-state
    (let [{:keys [board current-player scores width height]} game-state
          pos (types/position x y)
          new-board (types/set-cell board pos current-player)
          captured-points (find-captured-enemy-points new-board current-player width height)
          board-after-capture (if (empty? captured-points)
                               new-board
                               (capture-points new-board captured-points))
          had-capture (not (empty? captured-points))
          new-scores (if had-capture
                      (update scores current-player + (count captured-points))
                      scores)
          next-player (if had-capture current-player (switch-player current-player))
          game-over (game-ended? board-after-capture width height)
          winner (when game-over (determine-winner new-scores))]

      (js/console.log (str "Игрок " current-player " поставил точку на (" x ", " y ")"))
      (when had-capture
        (js/console.log (str "Захвачено " (count captured-points) " точек!")))

      (assoc game-state
             :board board-after-capture
             :current-player next-player
             :scores new-scores
             :game-over game-over
             :winner winner))))

;; Получение состояния игры для клиента
(defn get-game-state [game-state]
  (select-keys game-state [:board :current-player :scores :game-over :winner]))
