(ns cljs-dots-game.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [cljs-dots-game.game :as game]
            [cljs-dots-game.types :as types]
            ["socket.io-client" :as io]))

;; Состояние приложения
(defonce app-state (r/atom {:game-state nil
                            :socket nil
                            :room-id nil
                            :player-id nil
                            :connected false
                            :waiting-for-player false
                            :players-count 0}))

;; WebSocket подключение
(defonce socket (atom nil))

;; Функции для работы с сокетом
(defn connect-socket! []
  (let [new-socket (io "http://localhost:3000")]
    (reset! socket new-socket)
    (swap! app-state assoc :socket new-socket)

    ;; Обработчики событий
    (.on new-socket "connect"
         (fn []
           (js/console.log "Подключен к серверу")
           (swap! app-state assoc :connected true)
           ;; Автоматически создаем комнату при подключении
           (.emit new-socket "createRoom")))

    (.on new-socket "roomCreated"
         (fn [^js data]
           (let [room-id (.-roomId data)
                 player-id (.-playerId data)]
             (js/console.log (str "Комната создана: " room-id))
             (swap! app-state assoc
                    :room-id room-id
                    :player-id player-id
                    :waiting-for-player true
                    :players-count 1))))

    (.on new-socket "roomJoined"
         (fn [^js data]
           (let [room-id (.-roomId data)
                 player-id (.-playerId data)]
             (js/console.log (str "Присоединился к комнате: " room-id))
             (swap! app-state assoc
                    :room-id room-id
                    :player-id player-id
                    :waiting-for-player false
                    :players-count 2))))

    (.on new-socket "gameState"
         (fn [^js data]
           (let [game-state (js->clj data :keywordize-keys true)]
             (js/console.log "Получено состояние игры:" game-state)
             (js/console.log "Текущее состояние app-state:" (clj->js @app-state))
             (swap! app-state assoc :game-state game-state)
             (js/console.log "Обновленное состояние app-state:" (clj->js @app-state)))))

    (.on new-socket "playerJoined"
         (fn [^js data]
           (js/console.log "Второй игрок присоединился!")
           (swap! app-state assoc
                  :waiting-for-player false
                  :players-count 2)))

    (.on new-socket "playerLeft"
         (fn []
           (js/console.log "Игрок покинул игру")
           (swap! app-state assoc
                  :waiting-for-player true
                  :players-count 1)))

    (.on new-socket "error"
         (fn [^js data]
           (js/console.error "Ошибка:" (.-message data))))

    (.on new-socket "disconnect"
         (fn []
           (js/console.log "Отключен от сервера")
           (swap! app-state assoc :connected false)))))

;; Присоединение к существующей комнате
(defn join-room! [room-id]
  (when-let [socket-conn @socket]
    (.emit socket-conn "joinRoom" #js {:roomId room-id})))

;; Обработчик клика по клетке
(defn handle-cell-click [x y]
  (when-let [socket-conn @socket]
    (.emit socket-conn "makeMove" #js {:x x :y y})))

;; Компонент клетки
(defn cell-component [x y cell-value]
  (let [{:keys [waiting-for-player]} @app-state
        class-name (cond
                    (= cell-value (:player1 types/player)) "cell player1"
                    (= cell-value (:player2 types/player)) "cell player2"
                    (= cell-value (:captured types/cell-state)) "cell captured"
                    :else "cell empty")]
    [:div {:class class-name
           :on-click (when-not waiting-for-player #(handle-cell-click x y))}
     (cond
       (= cell-value (:player1 types/player)) "●"
       (= cell-value (:player2 types/player)) "●"
       (= cell-value (:captured types/cell-state)) "✕"
       :else "")]))

;; Компонент игровой доски
(defn board-component []
  (let [{:keys [game-state waiting-for-player]} @app-state]
    (if game-state
      (let [{:keys [board width height]} game-state]
        [:div.board
         (for [y (range height)]
           ^{:key y}
           [:div.row
            (for [x (range width)]
              ^{:key (str x "-" y)}
              [cell-component x y (get-in board [y x])])])])
      [:div.board-placeholder
       (if waiting-for-player
         "Ожидание игры..."
         "Подключение к серверу...")])))

;; Компонент статуса подключения
(defn connection-status-component []
  (let [{:keys [connected waiting-for-player room-id players-count]} @app-state]
    [:div.connection-status
     (cond
       (not connected)
       [:div
        [:p "Подключение к серверу..."]
        [:button {:on-click connect-socket!} "Переподключиться"]]

       waiting-for-player
       [:div
        [:p "Ожидание второго игрока"]
        [:p (str "Комната: " room-id)]
        [:p "Поделитесь этим кодом с другим игроком:"]
        [:div.room-code room-id]
        [:div.join-section
         [:p "Или введите код комнаты для присоединения:"]
         [:input {:id "room-input" :placeholder "Код комнаты"}]
         [:button {:on-click #(let [input-room-id (.-value (.getElementById js/document "room-input"))]
                                (when (not-empty input-room-id)
                                  (join-room! input-room-id)))}
          "Присоединиться"]]]

       :else
       [:div
        [:p "Игра началась!"]
        [:p (str "Игроков в комнате: " players-count)]])]))

;; Компонент информации об игре
(defn game-info-component []
  (let [{:keys [game-state waiting-for-player]} @app-state]
    (if (and game-state (not waiting-for-player))
      (let [{:keys [current-player scores game-over winner]} game-state
            score1 (get scores (:player1 types/player))
            score2 (get scores (:player2 types/player))]
        [:div.game-info
         [:h2 "Игра \"Точки\""]
         [:div.scores
          [:div (str "Игрок 1: " score1)]
          [:div (str "Игрок 2: " score2)]]
          ;; Этот иф существует номинально, не тестил
         (if game-over
           [:div.game-status
            (if winner
              (str "Победитель: Игрок " winner)
              "Ничья!")]
           [:div.game-status (str "Ход игрока: " current-player)])])
      [:div.game-info
       [:h2 "Игра \"Точки\""]])))

;; Главный компонент
(defn app-component []
  [:div.app
   [game-info-component]
   [connection-status-component]
   [board-component]])

;; Автоматическое подключение при загрузке
(defn auto-connect! []
  (js/setTimeout connect-socket! 100))

;; Инициализация приложения
(defn ^:export init! []
  (let [container (.getElementById js/document "app")
        root (rdom/create-root container)]
    (rdom/render root [app-component])
    ;; Автоматически подключаемся при старте
    (auto-connect!)))
