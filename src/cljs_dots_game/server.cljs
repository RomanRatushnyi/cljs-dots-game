(ns cljs-dots-game.server
  (:require [cljs-dots-game.game :as game]
            [cljs-dots-game.types :as types]
            ["express" :as express]
            ["http" :as http]
            ["socket.io" :as socket-io]
            ["path" :as path]
            ["uuid" :as uuid]))

;; Состояние сервера
(defonce server-state (atom {:rooms {}
                             :player-rooms {}}))

;; Создание комнаты
(defn create-room []
  (let [room-id (.v4 uuid)
        player-id (.v4 uuid)
        new-room {:id room-id
                  :game (game/init-game 10 10)
                  :players {}
                  :sockets {}}]
    (swap! server-state assoc-in [:rooms room-id] new-room)
    {:room-id room-id :player-id player-id}))

;; Присоединение к комнате
(defn join-room [room-id socket-id]
  (let [player-id (.v4 uuid)]
    (when (get-in @server-state [:rooms room-id])
      (swap! server-state assoc-in [:rooms room-id :sockets socket-id] player-id)
      (swap! server-state assoc-in [:rooms room-id :players player-id]
             {:id player-id :name (str "Игрок " (inc (count (get-in @server-state [:rooms room-id :players]))))})
      (swap! server-state assoc-in [:player-rooms socket-id] room-id)
      {:room-id room-id :player-id player-id})))

;; Выход из комнаты
(defn leave-room [socket-id]
  (when-let [room-id (get-in @server-state [:player-rooms socket-id])]
    (let [player-id (get-in @server-state [:rooms room-id :sockets socket-id])]
      (swap! server-state update-in [:rooms room-id :sockets] dissoc socket-id)
      (swap! server-state update-in [:rooms room-id :players] dissoc player-id)
      (swap! server-state update-in [:player-rooms] dissoc socket-id)
      (when (empty? (get-in @server-state [:rooms room-id :sockets]))
        (swap! server-state update-in [:rooms] dissoc room-id)))))

;; Выполнение хода
(defn make-move [socket-id x y]
  (when-let [room-id (get-in @server-state [:player-rooms socket-id])]
    (let [room (get-in @server-state [:rooms room-id])
          game-state (:game room)
          new-game-state (game/make-move game-state x y)]
      (swap! server-state assoc-in [:rooms room-id :game] new-game-state)
      {:room-id room-id :game-state (game/get-game-state new-game-state)})))

;; Получение состояния игры
(defn get-game-state [room-id]
  (when-let [room (get-in @server-state [:rooms room-id])]
    (game/get-game-state (:game room))))

;; Настройка Express
(defn setup-express [app]
  (.use app (express/static (path/join js/__dirname "../public")))
  (.get app "/" (fn [req res]
                  (.sendFile ^js res (path/join js/__dirname "../public/index.html")))))

;; Настройка Socket.IO
(defn setup-socket-io [^js io]
  (.on io "connection"
       (fn [^js socket]
         (js/console.log (str "Игрок подключился: " (.-id socket)))

         ;; Создание комнаты
         (.on ^js socket "createRoom"
              (fn []
                (let [{:keys [room-id player-id]} (create-room)]
                  (.join ^js socket room-id)
                  (.emit ^js socket "roomCreated" #js {:roomId room-id :playerId player-id})
                  (js/console.log (str "Создана комната: " room-id)))))

         ;; Присоединение к комнате
         (.on ^js socket "joinRoom"
              (fn [^js data]
                (let [room-id (.-roomId data)
                      result (join-room room-id (.-id socket))]
                  (if result
                    (do
                      (.join ^js socket room-id)
                      (.emit ^js socket "roomJoined" #js {:roomId room-id :playerId (:player-id result)})
                      (.to (.in ^js io room-id) "playerJoined" #js {:playerId (:player-id result)})
                      (when-let [game-state (get-game-state room-id)]
                        (.to (.in ^js io room-id) "gameState" (clj->js game-state)))
                      (js/console.log (str "Игрок присоединился к комнате: " room-id)))
                    (.emit ^js socket "error" #js {:message "Комната не найдена"})))))

         ;; Выполнение хода
         (.on ^js socket "makeMove"
              (fn [^js data]
                (let [x (.-x data)
                      y (.-y data)
                      result (make-move (.-id socket) x y)]
                  (when result
                    (.to (.in ^js io (:room-id result)) "gameState" (clj->js (:game-state result)))))))

         ;; Отключение
         (.on ^js socket "disconnect"
              (fn []
                (js/console.log (str "Игрок отключился: " (.-id socket)))
                (when-let [room-id (get-in @server-state [:player-rooms (.-id socket)])]
                  (leave-room (.-id socket))
                  (.to (.in ^js io room-id) "playerLeft")))))))

;; Главная функция сервера
(defn main []
  (let [app (express)
        server (.createServer http app)
        io (socket-io server #js {:cors #js {:origin "*" :methods #js ["GET" "POST"]}})]

    (setup-express app)
    (setup-socket-io io)

    (.listen server 3000
             (fn []
               (js/console.log "Сервер запущен на порту 3000")))))
