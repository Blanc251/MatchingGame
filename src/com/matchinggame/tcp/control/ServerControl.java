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
    
    private DatabaseManager dbManager; 
    private Map<String, Player> allPlayersData = new ConcurrentHashMap<>();

    private Map<String, Timer> turnTimers = Collections.synchronizedMap(new java.util.concurrent.ConcurrentHashMap<>());
    private Map<String, int[]> flippedCardsMap = Collections.synchronizedMap(new java.util.concurrent.ConcurrentHashMap<>());
    private Map<String, Integer> flipCountMap = Collections.synchronizedMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static final int TURN_DURATION_MS = 10000;

    public ServerControl(ServerView view) {
        this.view = view;
        connectedClients = Collections.synchronizedList(new ArrayList<>());
        onlinePlayers = Collections.synchronizedList(new ArrayList<>());
        activeRooms = Collections.synchronizedList(new ArrayList<>());
        
        dbManager = new DatabaseManager(); 
        allPlayersData.putAll(
            dbManager.loadAllPlayers().stream()
                .collect(Collectors.toMap(
                    p -> p.getUsername().toLowerCase(), 
                    p -> p
                ))
        );
        view.logMessage("Loaded " + allPlayersData.size() + " players from the database.");
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            view.logMessage("Server is running on port " + PORT + "...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                view.logMessage("New client connected from: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                connectedClients.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            view.logMessage("Server Error: " + e.getMessage());
        }
    }
    
    private void updatePlayerScoreAndWins(Player player, int scoreChange, boolean isWinner) {
        Player dataPlayer = allPlayersData.get(player.getUsername().toLowerCase());
        if (dataPlayer != null) {
            dataPlayer.setTotalScore(dataPlayer.getTotalScore() + scoreChange);
            if (isWinner) {
                dataPlayer.setTotalWins(dataPlayer.getTotalWins() + 1);
            }
            
            dbManager.updatePlayerScoreAndWins(dataPlayer); 
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
            Player dbPlayer = dbManager.getPlayerByUsername(username);
            if (dbPlayer != null) {
                allPlayersData.put(username.toLowerCase(), dbPlayer);
                return dbPlayer;
            } else {
                int initialScore = 0; 
                Player newPlayer = new Player(username, initialScore, "Offline", 0);
                dbManager.insertPlayer(newPlayer); 
                allPlayersData.put(username.toLowerCase(), newPlayer);
                return newPlayer;
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
        view.logMessage("[LOGIN] " + player.getUsername() + " has logged in. (Score: " + player.getTotalScore() + ")");
        broadcastRoomList();
        broadcastPlayerScoreUpdate();
    }

    public void removePlayer(String username) {
        Player p = allPlayersData.get(username.toLowerCase());
        if (p != null) {
            p.setStatus("Offline");
        }
        synchronized (onlinePlayers) {
            onlinePlayers.removeIf(player -> player.getUsername().equalsIgnoreCase(username));
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
            view.logMessage("[DISCONNECT] " + username + " has disconnected.");
            broadcastPlayerList();
            broadcastPlayerScoreUpdate();
        }
        view.logMessage("Total connected clients: " + connectedClients.size());
    }

    public void broadcastPlayerList() {
        view.logMessage("Broadcasting player list... (" + onlinePlayers.size() + " users online)");
        
        List<Player> lobbyPlayers;
        synchronized (allPlayersData) {
            lobbyPlayers = allPlayersData.values().stream()
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
        view.logMessage("[ERROR] + " + error);
    }
    
    public void handleCreateRoom(ClientHandler handler, int cardCount) {
        Player host = handler.getPlayer();
        if (host == null) return;

        String roomId = "Room-" + UUID.randomUUID().toString().substring(0, 4);
        GameRoom newRoom = new GameRoom(roomId, host, cardCount);
        activeRooms.add(newRoom);
        
        host.setStatus("Online");
        
        handler.sendMessage(new Command(Command.Type.CREATE_ROOM_SUCCESS, "SERVER", newRoom));
        
        broadcastPlayerList();
        broadcastRoomList();
        view.logMessage("New room created: " + roomId + " by " + host.getUsername());
    }

    public void handleCreateRoomAndInvite(ClientHandler hostHandler, InviteData data) {
        Player host = hostHandler.getPlayer();
        if (host == null) return;

        int cardCount = data.getCardCount();
        String roomId = "Room-" + UUID.randomUUID().toString().substring(0, 4);
        GameRoom newRoom = new GameRoom(roomId, host, cardCount);
        activeRooms.add(newRoom);
        
        host.setStatus("Online");
        
        hostHandler.sendMessage(new Command(Command.Type.CREATE_ROOM_SUCCESS, "SERVER", newRoom));
        
        String targetUsername = data.getTargetUsername();
        ClientHandler targetHandler = findClientHandler(targetUsername);
        
        if (targetHandler != null) {
            if (!"Online".equals(targetHandler.getPlayer().getStatus())) {
                hostHandler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Player " + targetUsername + " is busy."));
                return;
            }
            
            String inviterName = host.getUsername();
            Command inviteCommand = new Command(Command.Type.RECEIVE_INVITE, inviterName, newRoom.getRoomId());
            targetHandler.sendMessage(inviteCommand);
        } else {
            hostHandler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Error: Player " + targetUsername + " not found."));
        }
        
        broadcastPlayerList();
        broadcastRoomList();
        view.logMessage("New room: " + roomId + " (Host: " + host.getUsername() + ", Invited: " + targetUsername + ")");
    }
    
    public void handleDeclineInvite(ClientHandler targetHandler, String roomId) {
        GameRoom room = findRoomById(roomId);
        if (room == null) return;
        
        Player host = room.getHost();
        ClientHandler hostHandler = findClientHandler(host.getUsername());
        
        if (hostHandler != null) {
            String targetUsername = targetHandler.getPlayer().getUsername();
            Command declineNotif = new Command(Command.Type.RECEIVE_INVITE_DECLINED, targetUsername, "Player " + targetUsername + " declined the invite.");
            hostHandler.sendMessage(declineNotif);
        }
    }
    
    public void handleInvitePlayer(ClientHandler inviter, String targetUsername) {
        GameRoom room = findRoomByPlayer(inviter.getPlayer().getUsername());
        if (room == null || !room.getHost().equals(inviter.getPlayer())) {
            inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Error: You are not the host."));
            return;
        }

        ClientHandler targetHandler = findClientHandler(targetUsername);
        if (targetHandler != null) {
            if (!"Online".equals(targetHandler.getPlayer().getStatus())) {
                inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Player " + targetUsername + " is busy."));
                return;
            }
            
            String inviterName = inviter.getPlayer().getUsername();
            Command inviteCommand = new Command(Command.Type.RECEIVE_INVITE, inviterName, room.getRoomId());
            targetHandler.sendMessage(inviteCommand);
            
            inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Invite sent to " + targetUsername));
        } else {
            inviter.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Error: Player " + targetUsername + " not found."));
        }
    }

    public void handleJoinRoom(ClientHandler handler, String roomId) {
    Player player = handler.getPlayer();

    GameRoom oldRoom = findRoomByPlayer(player.getUsername());
    if (oldRoom != null) {
        view.logMessage("[JOIN] Player " + player.getUsername() + " is leaving old room " + oldRoom.getRoomId() + " to join " + roomId);
        synchronized(oldRoom.getPlayers()) { 
            oldRoom.removePlayer(player); 
        }
        
        if (oldRoom.getPlayerCount() == 0) {
            activeRooms.remove(oldRoom);
            view.logMessage("Room " + oldRoom.getRoomId() + " was disbanded (empty).");
        } else {
            Player remainingPlayer = oldRoom.getPlayers().get(0);
            if (player.equals(oldRoom.getHost())) { 
                oldRoom.setHost(remainingPlayer); 
            }
            remainingPlayer.setStatus("Online");
            oldRoom.setStatus("WAITING");
            oldRoom.getReadyPlayers().clear();
            oldRoom.getReadyPlayers().add(remainingPlayer.getUsername());
            oldRoom.getRematchStatus().clear();
            broadcastRoomState(oldRoom);
        }
        broadcastRoomList();
    }
    
    if (player == null || !"Online".equals(player.getStatus())) {
        handler.sendMessage(new Command(Command.Type.JOIN_ROOM_FAILED, "SERVER", "You are already in another room."));
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
            
            if (room.getPlayerCount() == room.getMaxPlayers()) {
                room.getHost().setStatus("InRoom");
                player.setStatus("InRoom");
            }
            
            view.logMessage("[JOIN] " + player.getUsername() + " joined room " + roomId);
            view.logMessage("[JOIN] Room now has " + room.getPlayerCount() + " players: " + 
                          room.getPlayers().stream().map(Player::getUsername).collect(Collectors.toList()));
            
            Command successCmd = new Command(Command.Type.JOIN_ROOM_SUCCESS, "SERVER", room);
            handler.sendMessage(successCmd);
            
            
            broadcastRoomState(room);
            
            broadcastPlayerList();
            broadcastRoomList();
        } else {
            handler.sendMessage(new Command(Command.Type.JOIN_ROOM_FAILED, "SERVER", "Room is full or you are already in it."));
        }
    } else {
        handler.sendMessage(new Command(Command.Type.JOIN_ROOM_FAILED, "SERVER", "Room not found."));
    }
}

    public void handleLeaveRoom(ClientHandler handler, String roomId) {
        Player player = handler.getPlayer();
        GameRoom room = findRoomById(roomId);

        if (player == null || room == null) return;

        cleanupRoomTimer(roomId);
        
        boolean wasPlaying = "PLAYING".equals(room.getStatus()) || "FINISHED".equals(room.getStatus());
        boolean wasHost = player.equals(room.getHost());
        
        if (wasPlaying && room.getPlayerCount() == 2) {
            Player winner = room.getPlayers().stream()
                    .filter(p -> !p.getUsername().equals(player.getUsername()))
                    .findFirst().orElse(null);
            
            if (winner != null) {
                int winnerMatchScore = room.getGameState().getScores().getOrDefault(winner.getUsername(), 0);
                int bonusScore = 5;
                
                updatePlayerScoreAndWins(winner, winnerMatchScore + bonusScore, true); 
                updatePlayerScoreAndWins(player, 0, false); 

                room.getGameState().setMessage(player.getUsername() + " left! " + winner.getUsername() + " wins!");
                room.getGameState().setGameStatus("FINISHED");
                room.getGameState().setTurnDuration(0);
                Command endCmd = new Command(Command.Type.OPPONENT_LEFT, "SERVER", room.getGameState());
                broadcastToRoom(room, endCmd, handler);
            }
        }

        synchronized (room.getPlayers()) {
            room.removePlayer(player);
        }
        player.setStatus("Online");
        
        if (room.getPlayerCount() == 0) {
            activeRooms.remove(room);
            view.logMessage("Room " + roomId + " was disbanded (empty).");
        } else {
            Player remainingPlayer = room.getPlayers().get(0);
            
            remainingPlayer.setStatus("Online");
            
            if (wasHost) {
                 view.logMessage("Host " + player.getUsername() + " left room " + roomId);
                 room.setHost(remainingPlayer);
                 view.logMessage(remainingPlayer.getUsername() + " is the new host.");
            } else {
                 view.logMessage("Player " + player.getUsername() + " left room " + roomId);
            }
            
            room.setStatus("WAITING");
            room.getReadyPlayers().clear();
            room.getReadyPlayers().add(remainingPlayer.getUsername());
            room.getRematchStatus().clear();
            
            broadcastRoomState(room); 
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
        view.logMessage(player.getUsername() + " requested a rematch in room " + room.getRoomId());

        if (room.getRematchStatus().values().stream().allMatch(b -> b == true) && room.getPlayerCount() == 2) {
            view.logMessage("Both players agreed to a rematch. Starting new game.");
            Player host = room.getHost();
            Player player2 = room.getPlayers().stream().filter(p -> !p.equals(host)).findFirst().orElse(null);
            
            room.readyPlayers.clear();
            room.readyPlayers.add(host.getUsername());
            if(player2 != null) {
                room.readyPlayers.add(player2.getUsername());
            }
            
            room.initializeGame();
            
            room.getGameState().setTurnStartTime(System.currentTimeMillis());
            room.getGameState().setTurnDuration(TURN_DURATION_MS);
            
            flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
            flipCountMap.put(room.getRoomId(), 0);
            
            Command gameStartedCmd = new Command(Command.Type.GAME_STARTED, "SERVER", room.getGameState());
            broadcastToRoom(room, gameStartedCmd, null);
            
            startTurnTimer(room);
            
        } else {
            Command rematchRequestCmd = new Command(Command.Type.REMATCH_REQUEST, player.getUsername(), "Rematch request sent. Waiting for opponent.");
            handler.sendMessage(rematchRequestCmd);
            
            Player opponent = room.getPlayers().stream().filter(p -> !p.equals(player)).findFirst().orElse(null);
            if (opponent != null) {
                ClientHandler opponentHandler = findClientHandler(opponent.getUsername());
                if (opponentHandler != null) {
                    Command opponentRematchCmd = new Command(Command.Type.REMATCH_REQUEST, player.getUsername(), "Opponent wants a rematch. Do you agree?");
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
            view.logMessage(player.getUsername() + " (guest) agreed to a rematch in room " + room.getRoomId());
            
            room.resetForRematch();
            room.setPlayerReady(player.getUsername());
            
            broadcastRoomState(room);
            
        } else {
            view.logMessage(player.getUsername() + " declined the rematch in room " + room.getRoomId());
            
            synchronized (room.getPlayers()) {
                room.removePlayer(player);
            }
            player.setStatus("Online");
            
            Command leaveCmd = new Command(Command.Type.LEAVE_ROOM, "SERVER", "You have left the room.");
            handler.sendMessage(leaveCmd);

            room.resetForRematch();
            
            if (room.getPlayerCount() > 0) {
                Player remainingPlayer = room.getPlayers().get(0);
                remainingPlayer.setStatus("Online");
            }
            
            broadcastRoomState(room);
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
                handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "You are the host, you are ready by default."));
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
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Error: You are not the host."));
            return;
        }
        
        if (room.getPlayerCount() < 2) {
             handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Error: Need 2 players to start."));
             return;
        }

        if (!room.areAllPlayersReady()) {
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Error: Not all players are ready."));
            return;
        }

        room.initializeGame();
        
        room.getGameState().setTurnStartTime(System.currentTimeMillis());
        room.getGameState().setTurnDuration(TURN_DURATION_MS);
        
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
        }, TURN_DURATION_MS);
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
        gameState.setMessage("Time's up! Switching to " + gameState.getCurrentPlayerUsername() + "'s turn.");
        
        gameState.setTurnStartTime(System.currentTimeMillis());
        gameState.setTurnDuration(TURN_DURATION_MS);
        
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
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "It's not your turn."));
            return;
        }
        
        int currentFlipCount = flipCountMap.getOrDefault(roomId, 0);
        if (currentFlipCount >= 2) {
            handler.sendMessage(new Command(Command.Type.CHAT_MESSAGE, "SERVER", "Two cards flipped. Please wait for result."));
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
            gameState.setMessage(player.getUsername() + " flipped 1 card...");
            
            gameState.setTurnStartTime(System.currentTimeMillis());
            gameState.setTurnDuration(TURN_DURATION_MS);
            
            Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
            broadcastToRoom(room, updateCmd, null);
            
            flipCountMap.put(roomId, newFlipCount);
            flippedCardsMap.put(roomId, flippedIndices);
            
            startTurnTimer(room); 

        } else if (newFlipCount == 2) {
            flippedIndices[1] = cardIndex;
            gameState.setMessage(player.getUsername() + " flipped 2 cards...");
            
            gameState.setTurnStartTime(System.currentTimeMillis());
            gameState.setTurnDuration(TURN_DURATION_MS);

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
        
        gameState.setMessage("Get Ready!");
        gameState.setTurnDuration(2000); 
        Command readyCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
        broadcastToRoom(room, readyCmd, null);
        
        Timer delayTimer = new Timer();
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                String currentMessage;
                
                if (card1.equals(card2)) {
                    gameState.setCardMatched(flippedIndices[0], true);
                    gameState.setCardMatched(flippedIndices[1], true);
                    
                    Map<String, Integer> scores = gameState.getScores();
                    scores.put(player.getUsername(), scores.get(player.getUsername()) + 10);
                    
                    if (isGameFinished(gameState)) {
                        handleGameOver(room, scores);
                        return; 
                    }
                    
                    currentMessage = player.getUsername() + " scored a point!";
                } else {
                    gameState.setCardFlipped(flippedIndices[0], false);
                    gameState.setCardFlipped(flippedIndices[1], false);
                    
                    currentMessage = "No match!";
                }
                
                switchPlayerTurn(room);
                
                gameState.setMessage(currentMessage + " Turn: " + gameState.getCurrentPlayerUsername());
                
                gameState.setTurnStartTime(System.currentTimeMillis());
                gameState.setTurnDuration(TURN_DURATION_MS);
                
                Command updateCmd = new Command(Command.Type.GAME_UPDATE, "SERVER", gameState);
                broadcastToRoom(room, updateCmd, null);
                
                flipCountMap.put(room.getRoomId(), 0);
                flippedCardsMap.put(room.getRoomId(), new int[]{-1, -1});
                startTurnTimer(room);
            }
        }, 2000);
    }
    
    private void handleGameOver(GameRoom room, Map<String, Integer> finalScores) {
        room.setStatus("FINISHED");
        room.getGameState().setGameStatus("FINISHED");
        
        List<Map.Entry<String, Integer>> sortedScores = finalScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());
        
        String winnerName = sortedScores.get(0).getKey();
        int winnerScore = sortedScores.get(0).getValue();
        int opponentScore = (sortedScores.size() > 1) ? sortedScores.get(1).getValue() : 0;
        
        String message = "Game Over!";
        
        boolean isDraw = (sortedScores.size() > 1) && (winnerScore == opponentScore);
        
        if (isDraw) {
            message = "Game Over! It's a draw with " + winnerScore + " points!";
        } else {
            message = "Game Over! " + winnerName + " wins with " + winnerScore + " points!";
        }
        
        for (Player p : room.getPlayers()) {
            boolean isWinner = false;
            int matchScore = finalScores.getOrDefault(p.getUsername(), 0);
            int bonusScore = 0;

            if (isDraw) {
            
            } else if (p.getUsername().equals(winnerName)) {
                isWinner = true;
                bonusScore = 10;
            }
            
            int dbScoreChange = matchScore + bonusScore;
            
            updatePlayerScoreAndWins(p, dbScoreChange, isWinner); 
        }
        
        room.getGameState().setMessage(message);
        room.getGameState().setTurnDuration(0);
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