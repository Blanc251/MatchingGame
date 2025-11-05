package com.matchinggame.tcp.control;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    
    private Map<String, Player> allPlayersData = new ConcurrentHashMap<>();

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
            
            mockLoadInitialPlayers();

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
    
    private void mockLoadInitialPlayers() {
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            Player p = new Player("MockUser" + i, rand.nextInt(50) * 10, "Offline");
            p.setTotalWins(rand.nextInt(10));
            allPlayersData.put(p.getUsername().toLowerCase(), p);
        }
    }
    
    private void mockUpdatePlayerScoreAndWins(Player player, int scoreChange, boolean isWinner) {
        Player dataPlayer = allPlayersData.get(player.getUsername().toLowerCase());
        if (dataPlayer != null) {
            dataPlayer.setTotalScore(dataPlayer.getTotalScore() + scoreChange);
            if (isWinner) {
                dataPlayer.setTotalWins(dataPlayer.getTotalWins() + 1);
            }
        }
    }
    
    public List<Player> getLeaderboard() {
        synchronized (allPlayersData) {
            return allPlayersData.values().stream()
                    .sorted(Comparator.comparing(Player::getTotalScore, Comparator.reverseOrder())
                            .thenComparing(Player::getTotalWins, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
        }
    }

    public boolean isUserLoggedIn(String username) {
        synchronized (onlinePlayers) {
            return onlinePlayers.stream().anyMatch(p -> p.getUsername().equalsIgnoreCase(username));
        }
    }

    public Player findOrCreatePlayer(String username) {
        Player existingPlayer = allPlayersData.get(username.toLowerCase());
            
        if (existingPlayer != null) {
            return existingPlayer;
        } else {
            int mockScore = new Random().nextInt(100) * 10; 
            Player newPlayer = new Player(username, mockScore, "Offline");
            allPlayersData.put(username.toLowerCase(), newPlayer);
            return newPlayer;
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
        broadcastPlayerScoreUpdate();
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
            broadcastPlayerScoreUpdate();
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
    
    public void broadcastPlayerScoreUpdate() {
        Command command = new Command(Command.Type.UPDATE_PLAYER_SCORE, "SERVER", getLeaderboard());
        
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
        boolean added = false;
        
        synchronized (room.getPlayers()) {
            if (room.getPlayerCount() < room.getMaxPlayers() && !room.getPlayers().contains(player)) {
                room.getPlayers().add(player);
                added = true;
            }
        }

        if (added) {
            player.setStatus("InRoom");
            
            view.logMessage("[JOIN] " + player.getUsername() + " joined room " + roomId);
            view.logMessage("[JOIN] Room now has " + room.getPlayerCount() + " players: " + 
                          room.getPlayers().stream().map(Player::getUsername).collect(Collectors.toList()));
            
            Command successCmd = new Command(Command.Type.JOIN_ROOM_SUCCESS, "SERVER", room);
            handler.sendMessage(successCmd);
            
            
            broadcastRoomState(room);
            
            broadcastPlayerList();
            broadcastRoomList();
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
        
        boolean wasPlaying = "PLAYING".equals(room.getStatus());
        Player winner = null;
        
        if (wasPlaying) {
            winner = room.getPlayers().stream()
                    .filter(p -> !p.getUsername().equals(player.getUsername()))
                    .findFirst().orElse(null);
            
            if (winner != null) {
                mockUpdatePlayerScoreAndWins(winner, 5, true); 
                room.getGameState().setMessage(player.getUsername() + " đã thoát! " + winner.getUsername() + " chiến thắng!");
                room.getGameState().setGameStatus("FINISHED");
                Command endCmd = new Command(Command.Type.GAME_OVER, "SERVER", room.getGameState());
                broadcastToRoom(room, endCmd, handler);
            }
        }

        synchronized (room.getPlayers()) {
            room.removePlayer(player);
        }
        player.setStatus("Online");
        
        if (room.getPlayerCount() == 0) {
            activeRooms.remove(room);
            view.logMessage("Phòng " + roomId + " đã bị giải tán.");
        } else {
            if (player.equals(room.getHost())) {
                activeRooms.remove(room);
                view.logMessage("Chủ phòng " + player.getUsername() + " đã rời, giải tán phòng " + roomId);
                Command closeCmd = new Command(Command.Type.LEAVE_ROOM, "SERVER", "Chủ phòng đã rời, phòng bị giải tán.");
                broadcastToRoom(room, closeCmd, handler);
            } else {
                if (wasPlaying && winner == null) { 
                    activeRooms.remove(room);
                    view.logMessage("Phòng " + roomId + " bị giải tán do đối thủ rời đi.");
                    Command closeCmd = new Command(Command.Type.LEAVE_ROOM, "SERVER", "Đối thủ đã rời, phòng bị giải tán.");
                    broadcastToRoom(room, closeCmd, handler);
                } else if (!wasPlaying) {
                    broadcastRoomState(room);
                }
            }
        }
        
        if (wasPlaying) {
            broadcastPlayerScoreUpdate();
        }
        broadcastPlayerList();
        broadcastRoomList();
    }
    
    public void handleQuitGame(ClientHandler handler) {
        GameRoom room = findRoomByPlayer(handler.getPlayer().getUsername());
        if (room != null) {
            handleLeaveRoom(handler, room.getRoomId());
        }
    }
    
    public void handleRematchRequest(ClientHandler handler) {
        Player player = handler.getPlayer();
        GameRoom room = findRoomByPlayer(player.getUsername());
        if (room == null || !room.getStatus().equals("FINISHED")) return;

        room.getRematchStatus().put(player.getUsername(), true);
        view.logMessage(player.getUsername() + " yêu cầu chơi lại trong phòng " + room.getRoomId());

        if (room.getRematchStatus().values().stream().allMatch(b -> b == true) && room.getPlayerCount() == 2) {
            view.logMessage("Cả hai đồng ý chơi lại. Tạo ván mới.");
            Player host = room.getHost();
            Player player2 = room.getPlayers().stream().filter(p -> !p.equals(host)).findFirst().orElse(null);
            
            room.readyPlayers.clear();
            room.readyPlayers.add(host.getUsername());
            if(player2 != null) {
                room.readyPlayers.add(player2.getUsername());
            }
            
            room.initializeGame();
            
            flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
            flipCountMap.put(room.getRoomId(), 0);
            
            Command gameStartedCmd = new Command(Command.Type.GAME_STARTED, "SERVER", room.getGameState());
            broadcastToRoom(room, gameStartedCmd, null);
            
            startTurnTimer(room);
            
        } else {
            Command rematchRequestCmd = new Command(Command.Type.REMATCH_REQUEST, player.getUsername(), "Đã gửi yêu cầu chơi lại. Chờ đối thủ.");
            handler.sendMessage(rematchRequestCmd);
            
            Player opponent = room.getPlayers().stream().filter(p -> !p.equals(player)).findFirst().orElse(null);
            if (opponent != null) {
                ClientHandler opponentHandler = findClientHandler(opponent.getUsername());
                if (opponentHandler != null) {
                    Command opponentRematchCmd = new Command(Command.Type.REMATCH_REQUEST, player.getUsername(), "Đối thủ muốn chơi lại. Bạn có đồng ý?");
                    opponentHandler.sendMessage(opponentRematchCmd);
                }
            }
        }
    }
    
    public void handleRematchResponse(ClientHandler handler, boolean accepted) {
        Player player = handler.getPlayer();
        GameRoom room = findRoomByPlayer(player.getUsername());
        if (room == null || !room.getStatus().equals("FINISHED")) return;
        
        if (accepted) {
            handleRematchRequest(handler);
        } else {
            view.logMessage(player.getUsername() + " từ chối chơi lại trong phòng " + room.getRoomId());
            
            Player opponent = room.getPlayers().stream().filter(p -> !p.equals(player)).findFirst().orElse(null);
            if (opponent != null) {
                ClientHandler opponentHandler = findClientHandler(opponent.getUsername());
                if (opponentHandler != null) {
                    Command closeCmd = new Command(Command.Type.LEAVE_ROOM, "SERVER", player.getUsername() + " đã từ chối chơi lại. Phòng bị giải tán.");
                    opponentHandler.sendMessage(closeCmd);
                }
            }
            
            room.getPlayers().forEach(p -> p.setStatus("Online"));
            activeRooms.remove(room);
            broadcastPlayerList();
            broadcastRoomList();
        }
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
    
    private void broadcastToRoom(GameRoom room, Command command, ClientHandler exclude) {
    List<Player> playersInRoom;
    
    synchronized (room.getPlayers()) {
        playersInRoom = new ArrayList<>(room.getPlayers());
    }

    view.logMessage("[BROADCAST_TO_ROOM] Room: " + room.getRoomId() + 
                    ", Command: " + command.getType() + 
                    ", Players in room: " + playersInRoom.size() +
                    ", Players: " + playersInRoom.stream().map(Player::getUsername).collect(Collectors.toList()));
    
    for (Player p : playersInRoom) {
        ClientHandler handler = findClientHandler(p.getUsername());
        if (handler != null && handler != exclude) {
            view.logMessage("[BROADCAST_TO_ROOM] Sending to " + p.getUsername());
            handler.sendMessage(command);
        } else {
            if (handler == null) {
                view.logMessage("[BROADCAST_TO_ROOM] Handler NULL for " + p.getUsername());
            }
        }
    }
}

public void broadcastRoomState(GameRoom room) {
    view.logMessage("[BROADCAST_ROOM_STATE] Room: " + room.getRoomId() + 
                    ", Players: " + room.getPlayerCount() +
                    ", Details: " + room.getPlayers().stream()
                                        .map(Player::getUsername)
                                        .collect(Collectors.toList()));
    
    Command roomStateCmd = new Command(Command.Type.UPDATE_ROOM_STATE, "SERVER", room);
    broadcastToRoom(room, roomStateCmd, null);
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
            if (player.getUsername().equalsIgnoreCase(room.getHost().getUsername())) {
                handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Bạn là chủ phòng, đã sẵn sàng mặc định."));
                return;
            }
            
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
    }

    public void handleFlipCard(ClientHandler handler, FlipData flipData) {
        Player player = handler.getPlayer();
        String roomId = flipData.getRoomId();
        int cardIndex = flipData.getCardIndex();
        
        GameRoom room = findRoomById(roomId);
        if (room == null || !room.getStatus().equals("PLAYING")) return;
        
        GameState gameState = room.getGameState();
        if (!player.getUsername().equals(gameState.getCurrentPlayerUsername())) {
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Chưa tới lượt của bạn."));
            return;
        }
        
        int currentFlipCount = flipCountMap.getOrDefault(roomId, 0);
        if (currentFlipCount >= 2) {
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Đã lật 2 lá. Vui lòng chờ kiểm tra kết quả."));
            return;
        }
        
        if (gameState.isCardFlipped()[cardIndex] || gameState.isCardMatched()[cardIndex]) {
            return;
        }
        
        Timer timer = turnTimers.get(roomId);
        if (timer != null) {
            timer.cancel(); 
            turnTimers.remove(roomId);
        }

        int newFlipCount = currentFlipCount + 1;
        int[] flippedIndices = flippedCardsMap.getOrDefault(roomId, new int[]{-1, -1});

        gameState.setCardFlipped(cardIndex, true);

        if (newFlipCount == 1) {
            flippedIndices[0] = cardIndex;
            gameState.setMessage(player.getUsername() + " đã lật 1 lá...");
            
            Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
            broadcastToRoom(room, updateCmd, null);
            
            flipCountMap.put(roomId, newFlipCount);
            flippedCardsMap.put(roomId, flippedIndices);
            
            startTurnTimer(room); 

        } else if (newFlipCount == 2) {
            flippedIndices[1] = cardIndex;
            gameState.setMessage(player.getUsername() + " đã lật 2 lá...");

            Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
            broadcastToRoom(room, updateCmd, null);
            
            flipCountMap.put(roomId, newFlipCount);
            flippedCardsMap.put(roomId, flippedIndices);
            
            checkMatch(room, player, flippedIndices);
        }
    }
    
    private void checkMatch(GameRoom room, Player player, int[] flippedIndices) {
        GameState gameState = room.getGameState();
        String card1 = gameState.getCardValues().get(flippedIndices[0]);
        String card2 = gameState.getCardValues().get(flippedIndices[1]);

        cleanupRoomTimer(room.getRoomId());
        
        Timer delayTimer = new Timer();
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (card1.equals(card2)) {
                    gameState.setCardMatched(flippedIndices[0], true);
                    gameState.setCardMatched(flippedIndices[1], true);
                    
                    Map<String, Integer> scores = gameState.getScores();
                    scores.put(player.getUsername(), scores.get(player.getUsername()) + 1);
                    
                    if (isGameFinished(gameState)) {
                        handleGameOver(room, scores);
                    } else {
                        gameState.setMessage(player.getUsername() + " ăn điểm! Bạn được đi tiếp.");
                        Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
                        broadcastToRoom(room, updateCmd, null);
                        
                        flipCountMap.put(room.getRoomId(), 0);
                        flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
                        startTurnTimer(room);
                    }
                    
                } else {
                    // CẬP NHẬT: Lật úp 2 thẻ bài
                    gameState.setCardFlipped(flippedIndices[0], false);
                    gameState.setCardFlipped(flippedIndices[1], false);
                    
                    switchPlayerTurn(room);
                    gameState.setMessage("Không khớp! Lượt của " + gameState.getCurrentPlayerUsername());
                    
                    Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
                    broadcastToRoom(room, updateCmd, null);
                    
                    flipCountMap.put(room.getRoomId(), 0);
                    flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
                    startTurnTimer(room);
                }
            }
        }, 2000);
    }
    
    private void handleGameOver(GameRoom room, Map<String, Integer> finalScores) {
        room.setStatus("FINISHED");
        room.getGameState().setGameStatus("FINISHED");
        
        Map.Entry<String, Integer> winnerEntry = Collections.max(finalScores.entrySet(), Map.Entry.comparingByValue());
        
        String winnerName = winnerEntry.getKey();
        int winnerScore = winnerEntry.getValue();
        
        String message = "Trò chơi kết thúc!";
        
        for (Player p : room.getPlayers()) {
            boolean isWinner = p.getUsername().equals(winnerName);
            int scoreChange = finalScores.get(p.getUsername());
            mockUpdatePlayerScoreAndWins(p, scoreChange, isWinner);
            
            if (isWinner) {
                message = "Trò chơi kết thúc! " + winnerName + " chiến thắng với " + winnerScore + " điểm!";
            }
        }
        
        room.getGameState().setMessage(message);
        Command endCmd = new Command(Command.Type.GAME_OVER, "SERVER", room.getGameState());
        broadcastToRoom(room, endCmd, null);
        
        cleanupRoomTimer(room.getRoomId());
        broadcastPlayerScoreUpdate();
        broadcastRoomList();
    }
    
    private boolean isGameFinished(GameState gameState) {
        for (boolean matched : gameState.isCardMatched()) {
            if (!matched) return false;
        }
        return true;
    }
}