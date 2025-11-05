package com.matchinggame.tcp.control;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.stream.Collectors;

import com.matchinggame.tcp.model.Command;
import com.matchinggame.tcp.model.FlipData;
import com.matchinggame.tcp.model.GameRoom;
import com.matchinggame.tcp.model.GameState;
import com.matchinggame.tcp.model.InviteData;
import com.matchinggame.tcp.model.Player;
import com.matchinggame.tcp.view.ServerView;

public class ServerControl {
    private ServerSocket serverSocket;
    private final int PORT = 9999;
    private List<ClientHandler> connectedClients;
    private List<Player> onlinePlayers;
    private List<GameRoom> activeRooms;
    private ServerView view;

    private Map<String, Timer> turnTimers = Collections.synchronizedMap(new java.util.concurrent.ConcurrentHashMap<>());
    private Map<String, int[]> flippedCardsMap = Collections.synchronizedMap(new java.util.concurrent.ConcurrentHashMap<>());
    private Map<String, Integer> flipCountMap = Collections.synchronizedMap(new java.util.concurrent.ConcurrentHashMap<>());

    public ServerControl(ServerView view) {
        this.view = view;
        connectedClients = Collections.synchronizedList(new ArrayList<>());
        onlinePlayers = Collections.synchronizedList(new ArrayList<>());
        activeRooms = Collections.synchronizedList(new ArrayList<>());
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            view.logMessage("Server đang chạy trên cổng " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                view.logMessage("Client mới kết nối từ: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                connectedClients.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            view.logMessage("Lỗi Server: " + e.getMessage());
        }
    }

    public boolean isUserLoggedIn(String username) {
        synchronized (onlinePlayers) {
            return onlinePlayers.stream().anyMatch(p -> p.getUsername().equalsIgnoreCase(username));
        }
    }

    public Player findOrCreatePlayer(String username) {
        synchronized (onlinePlayers) {
            Optional<Player> existingPlayer = onlinePlayers.stream()
                    .filter(p -> p.getUsername().equalsIgnoreCase(username))
                    .findFirst();
            
            if (existingPlayer.isPresent()) {
                return existingPlayer.get();
            } else {
                int mockScore = new Random().nextInt(1000); 
                return new Player(username, mockScore, "Offline");
            }
        }
    }

    public void addPlayer(Player player) {
        player.setStatus("Online");
        synchronized (onlinePlayers) {
            if (!onlinePlayers.contains(player)) {
                onlinePlayers.add(player);
            }
        }
        view.addUserToList(player.getUsername()); 
        view.logMessage("[LOGIN] " + player.getUsername() + " đã đăng nhập. (Score: " + player.getTotalScore() + ")");
        broadcastRoomList();
    }

    public void removePlayer(String username) {
        synchronized (onlinePlayers) {
            onlinePlayers.removeIf(p -> p.getUsername().equalsIgnoreCase(username));
        }
        view.removeUserFromList(username);
    }

    public void removeHandler(ClientHandler handler) {
        boolean removed = connectedClients.remove(handler);
        if (removed && handler.getPlayer() != null) {
            String username = handler.getPlayer().getUsername();
            
            GameRoom room = findRoomByPlayer(username);
            if (room != null) {
                handleLeaveRoom(handler, room.getRoomId());
            }

            removePlayer(username);
            view.logMessage("[DISCONNECT] " + username + " đã ngắt kết nối.");
            broadcastPlayerList();
        }
        view.logMessage("Tổng số Client đang kết nối: " + connectedClients.size());
    }

    public void broadcastPlayerList() {
        view.logMessage("Broadcasting player list... (" + onlinePlayers.size() + " users online)");
        
        List<Player> lobbyPlayers;
        synchronized (onlinePlayers) {
            lobbyPlayers = onlinePlayers.stream()
                .filter(p -> "Online".equals(p.getStatus()))
                .collect(Collectors.toList());
        }
            
        Command command = new Command(Command.Type.UPDATE_PLAYER_LIST, "SERVER", new ArrayList<>(lobbyPlayers));
        
        synchronized (connectedClients) {
            for (ClientHandler client : connectedClients) {
                if (client.getPlayer() != null) { 
                    client.sendMessage(command);
                }
            }
        }
    }
    
    public ArrayList<Player> getOnlinePlayers() {
        synchronized (onlinePlayers) {
            return new ArrayList<>(onlinePlayers);
        }
    }

    public void logError(String error) {
        view.logMessage("[ERROR] " + error);
    }
    
    public void handleCreateRoom(ClientHandler handler, int cardCount) {
        Player host = handler.getPlayer();
        if (host == null) return;

        String roomId = "Room-" + UUID.randomUUID().toString().substring(0, 4);
        GameRoom newRoom = new GameRoom(roomId, host, cardCount);
        activeRooms.add(newRoom);
        
        host.setStatus("InRoom");
        
        handler.sendMessage(new Command(Command.Type.CREATE_ROOM_SUCCESS, "SERVER", newRoom));
        
        broadcastPlayerList();
        broadcastRoomList();
        view.logMessage("Phòng mới được tạo: " + roomId + " bởi " + host.getUsername());
    }

    public void handleCreateRoomAndInvite(ClientHandler hostHandler, InviteData data) {
        Player host = hostHandler.getPlayer();
        if (host == null) return;

        int cardCount = data.getCardCount();
        String roomId = "Room-" + UUID.randomUUID().toString().substring(0, 4);
        GameRoom newRoom = new GameRoom(roomId, host, cardCount);
        activeRooms.add(newRoom);
        
        host.setStatus("InRoom");
        
        hostHandler.sendMessage(new Command(Command.Type.CREATE_ROOM_SUCCESS, "SERVER", newRoom));
        
        String targetUsername = data.getTargetUsername();
        ClientHandler targetHandler = findClientHandler(targetUsername);
        
        if (targetHandler != null) {
            if (!"Online".equals(targetHandler.getPlayer().getStatus())) {
                hostHandler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Người chơi " + targetUsername + " đang bận."));
                return;
            }
            
            String inviterName = host.getUsername();
            Command inviteCommand = new Command(Command.Type.RECEIVE_INVITE, inviterName, newRoom.getRoomId());
            targetHandler.sendMessage(inviteCommand);
        } else {
            hostHandler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Lỗi: Không tìm thấy người chơi " + targetUsername));
        }
        
        broadcastPlayerList();
        broadcastRoomList();
        view.logMessage("Phòng mới: " + roomId + " (Host: " + host.getUsername() + ", Mời: " + targetUsername + ")");
    }
    
    public void handleDeclineInvite(ClientHandler targetHandler, String roomId) {
        GameRoom room = findRoomById(roomId);
        if (room == null) return;
        
        Player host = room.getHost();
        ClientHandler hostHandler = findClientHandler(host.getUsername());
        
        if (hostHandler != null) {
            String targetUsername = targetHandler.getPlayer().getUsername();
            Command declineNotif = new Command(Command.Type.RECEIVE_INVITE_DECLINED, targetUsername, "Người chơi " + targetUsername + " đã từ chối lời mời.");
            hostHandler.sendMessage(declineNotif);
        }
    }
    
    public void handleInvitePlayer(ClientHandler inviter, String targetUsername) {
        GameRoom room = findRoomByPlayer(inviter.getPlayer().getUsername());
        if (room == null || !room.getHost().equals(inviter.getPlayer())) {
            inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Lỗi: Bạn không phải chủ phòng."));
            return;
        }

        ClientHandler targetHandler = findClientHandler(targetUsername);
        if (targetHandler != null) {
            if (!"Online".equals(targetHandler.getPlayer().getStatus())) {
                inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Người chơi " + targetUsername + " đang bận."));
                return;
            }
            
            String inviterName = inviter.getPlayer().getUsername();
            Command inviteCommand = new Command(Command.Type.RECEIVE_INVITE, inviterName, room.getRoomId());
            targetHandler.sendMessage(inviteCommand);
            
            inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Đã gửi lời mời tới " + targetUsername));
        } else {
            inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Lỗi: Không tìm thấy người chơi " + targetUsername));
        }
    }

    public void handleJoinRoom(ClientHandler handler, String roomId) {
        Player player = handler.getPlayer();
        if (player == null || !"Online".equals(player.getStatus())) {
            handler.sendMessage(new Command(Command.Type.JOIN_ROOM_FAILED, "SERVER", "Bạn đang ở trong phòng khác."));
            return;
        }

        GameRoom room = findRoomById(roomId);
        if (room != null) {
            boolean added;
            synchronized (room.getPlayers()) {
                added = room.addPlayer(player);
            }

            if (added) {
                player.setStatus("InRoom");
                
                Command successCmd = new Command(Command.Type.JOIN_ROOM_SUCCESS, "SERVER", room);
                handler.sendMessage(successCmd);
                
                broadcastPlayerList();
                broadcastRoomList();
                broadcastRoomState(room);
            } else {
                handler.sendMessage(new Command(Command.Type.JOIN_ROOM_FAILED, "SERVER", "Phòng đã đầy hoặc bạn đã ở trong phòng."));
            }
        } else {
            handler.sendMessage(new Command(Command.Type.JOIN_ROOM_FAILED, "SERVER", "Không tìm thấy phòng."));
        }
    }

    public void handleLeaveRoom(ClientHandler handler, String roomId) {
        Player player = handler.getPlayer();
        GameRoom room = findRoomById(roomId);

        if (player == null || room == null) return;

        cleanupRoomTimer(roomId);
        
        synchronized (room.getPlayers()) {
            room.removePlayer(player);
        }
        player.setStatus("Online");
        
        if (room.getPlayerCount() == 0) {
            activeRooms.remove(room);
            view.logMessage("Phòng " + roomId + " đã bị giải tán.");
        } else {
            if (player.equals(room.getHost()) || room.getStatus().equals("PLAYING")) {
                activeRooms.remove(room);
                view.logMessage("Chủ phòng " + player.getUsername() + " đã rời, giải tán phòng " + roomId);
                Command closeCmd = new Command(Command.Type.LEAVE_ROOM, "SERVER", "Chủ phòng đã rời, phòng bị giải tán.");
                broadcastToRoom(room, closeCmd, null);
            } else {
                broadcastRoomState(room);
            }
        }
        
        broadcastPlayerList();
        broadcastRoomList();
    }
    
    public void broadcastRoomList() {
        Command roomListCmd = new Command(Command.Type.UPDATE_ROOM_LIST, "SERVER", new ArrayList<>(activeRooms));
        synchronized (connectedClients) {
            for (ClientHandler client : connectedClients) {
                if (client.getPlayer() != null && "Online".equals(client.getPlayer().getStatus())) {
                    client.sendMessage(roomListCmd);
                }
            }
        }
    }
    
    public void broadcastRoomState(GameRoom room) {
        Command roomStateCmd = new Command(Command.Type.UPDATE_ROOM_STATE, "SERVER", room);
        broadcastToRoom(room, roomStateCmd, null);
    }
    
    private void broadcastToRoom(GameRoom room, Command command, ClientHandler exclude) {
        List<Player> playersInRoom;
        synchronized (room.getPlayers()) {
            playersInRoom = new ArrayList<>(room.getPlayers());
        }

        for (Player p : playersInRoom) {
            ClientHandler handler = findClientHandler(p.getUsername());
            if (handler != null && handler != exclude) {
                handler.sendMessage(command);
            }
        }
    }

    private ClientHandler findClientHandler(String username) {
        synchronized (connectedClients) {
            return connectedClients.stream()
                    .filter(h -> h.getPlayer() != null && h.getPlayer().getUsername().equalsIgnoreCase(username))
                    .findFirst()
                    .orElse(null);
        }
    }

    private GameRoom findRoomByPlayer(String username) {
        synchronized (activeRooms) {
            for (GameRoom r : activeRooms) {
                synchronized (r.getPlayers()) {
                    if (r.getPlayers().stream().anyMatch(p -> p.getUsername().equalsIgnoreCase(username))) {
                        return r;
                    }
                }
            }
            return null;
        }
    }

    private GameRoom findRoomById(String roomId) {
        synchronized (activeRooms) {
            return activeRooms.stream()
                    .filter(r -> r.getRoomId().equalsIgnoreCase(roomId))
                    .findFirst()
                    .orElse(null);
        }
    }

    public void handlePlayerReady(ClientHandler handler) {
        Player player = handler.getPlayer();
        GameRoom room = findRoomByPlayer(player.getUsername());
        if (room != null) {
            synchronized (room.getReadyPlayers()) {
                room.setPlayerReady(player.getUsername());
            }
            broadcastRoomState(room);
        }
    }

    public void handleStartGame(ClientHandler handler) {
        Player player = handler.getPlayer();
        GameRoom room = findRoomByPlayer(player.getUsername());

        if (room == null || !room.getHost().equals(player)) {
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Lỗi: Bạn không phải chủ phòng."));
            return;
        }
        
        if (room.getPlayerCount() < 2) {
             handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Lỗi: Cần 2 người chơi để bắt đầu."));
             return;
        }

        if (!room.areAllPlayersReady()) {
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Lỗi: Chưa tất cả người chơi sẵn sàng."));
            return;
        }

        room.initializeGame();
        
        flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
        flipCountMap.put(room.getRoomId(), 0);
        
        Command gameStartedCmd = new Command(Command.Type.GAME_STARTED, "SERVER", room.getGameState());
        broadcastToRoom(room, gameStartedCmd, null);
        
        startTurnTimer(room);
    }

    private void startTurnTimer(GameRoom room) {
        cleanupRoomTimer(room.getRoomId());
        
        Timer turnTimer = new Timer();
        turnTimers.put(room.getRoomId(), turnTimer);
        
        turnTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handleTurnTimeout(room);
            }
        }, 10000);
    }

    private void handleTurnTimeout(GameRoom room) {
        if (room == null || !room.getStatus().equals("PLAYING")) {
            return;
        }

        GameState gameState = room.getGameState();
        
        int flipCount = flipCountMap.getOrDefault(room.getRoomId(), 0);
        if (flipCount == 1) {
            int[] flippedIndices = flippedCardsMap.get(room.getRoomId());
            gameState.setCardFlipped(flippedIndices[0], false);
        }

        flipCountMap.put(room.getRoomId(), 0);
        flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
        
        switchPlayerTurn(room);
        gameState.setMessage("Hết giờ! Chuyển lượt của " + gameState.getCurrentPlayerUsername());
        
        Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
        broadcastToRoom(room, updateCmd, null);
        
        startTurnTimer(room);
    }

    private void switchPlayerTurn(GameRoom room) {
        GameState gameState = room.getGameState();
        String currentPlayer = gameState.getCurrentPlayerUsername();
        
        Player nextPlayer = room.getPlayers().stream()
                                .filter(p -> !p.getUsername().equals(currentPlayer))
                                .findFirst()
                                .orElse(room.getHost());
        gameState.setCurrentPlayerUsername(nextPlayer.getUsername());
    }

    private void cleanupRoomTimer(String roomId) {
        Timer oldTimer = turnTimers.remove(roomId);
        if (oldTimer != null) {
            oldTimer.cancel();
        }
        flippedCardsMap.remove(roomId);
        flipCountMap.remove(roomId);
    }

    public void handleFlipCard(ClientHandler handler, FlipData flipData) {
        Player player = handler.getPlayer();
        String roomId = flipData.getRoomId();
        int cardIndex = flipData.getCardIndex();
        
        GameRoom room = findRoomById(roomId);
        if (room == null || !room.getStatus().equals("PLAYING")) return;
        
        GameState gameState = room.getGameState();
        if (!player.getUsername().equals(gameState.getCurrentPlayerUsername())) {
            return;
        }
        
        if (gameState.isCardFlipped()[cardIndex] || gameState.isCardMatched()[cardIndex]) {
            return;
        }

        Timer timer = turnTimers.get(roomId);
        if (timer != null) {
            timer.cancel();
        }

        int flipCount = flipCountMap.getOrDefault(roomId, 0);
        int[] flippedIndices = flippedCardsMap.getOrDefault(roomId, new int[]{-1, -1});

        gameState.setCardFlipped(cardIndex, true);
        flipCount++;

        if (flipCount == 1) {
            flippedIndices[0] = cardIndex;
            gameState.setMessage(player.getUsername() + " đã lật 1 lá...");
            
            Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
            broadcastToRoom(room, updateCmd, null);
            
            flipCountMap.put(roomId, flipCount);
            flippedCardsMap.put(roomId, flippedIndices);
            startTurnTimer(room);

        } else if (flipCount == 2) {
            flippedIndices[1] = cardIndex;
            gameState.setMessage(player.getUsername() + " đã lật 2 lá...");

            Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
            broadcastToRoom(room, updateCmd, null);
            
            checkMatch(room, player, flippedIndices);
        }
    }
    
    private void checkMatch(GameRoom room, Player player, int[] flippedIndices) {
        GameState gameState = room.getGameState();
        String card1 = gameState.getCardValues().get(flippedIndices[0]);
        String card2 = gameState.getCardValues().get(flippedIndices[1]);

        Timer delayTimer = new Timer();
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (card1.equals(card2)) {
                    gameState.setCardMatched(flippedIndices[0], true);
                    gameState.setCardMatched(flippedIndices[1], true);
                    
                    Map<String, Integer> scores = gameState.getScores();
                    scores.put(player.getUsername(), scores.get(player.getUsername()) + 1);
                    gameState.setMessage(player.getUsername() + " ăn điểm! Bạn được đi tiếp.");

                    if (isGameFinished(gameState)) {
                        gameState.setGameStatus("FINISHED");
                        Map.Entry<String, Integer> winner = Collections.max(scores.entrySet(), Map.Entry.comparingByValue());
                        gameState.setMessage("Trò chơi kết thúc! " + winner.getKey() + " chiến thắng!");
                        Command endCmd = new Command(Command.Type.GAME_OVER, "SERVER", gameState);
                        broadcastToRoom(room, endCmd, null);
                        cleanupRoomTimer(room.getRoomId());
                        activeRooms.remove(room);
                        broadcastRoomList();
                    } else {
                        Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
                        broadcastToRoom(room, updateCmd, null);
                        startTurnTimer(room);
                    }
                    
                } else {
                    gameState.setCardFlipped(flippedIndices[0], false);
                    gameState.setCardFlipped(flippedIndices[1], false);
                    
                    switchPlayerTurn(room);
                    gameState.setMessage("Không khớp! Lượt của " + gameState.getCurrentPlayerUsername());
                    
                    Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
                    broadcastToRoom(room, updateCmd, null);
                    startTurnTimer(room);
                }
                
                flipCountMap.put(room.getRoomId(), 0);
                flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
            }
        }, 2000);
    }
    
    private boolean isGameFinished(GameState gameState) {
        for (boolean matched : gameState.isCardMatched()) {
            if (!matched) return false;
        }
        return true;
    }
}