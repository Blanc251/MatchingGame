package com.matchinggame.tcp.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoom implements Serializable {
    private static final long serialVersionUID = 10L;

    private String roomId;
    private Player host;
    private List<Player> players;
    private int maxPlayers;
    private int cardCount;
    private String status;
    
    public List<String> readyPlayers;
    private GameState gameState;
    
    private ConcurrentHashMap<String, Boolean> rematchStatus;
    
    private static final List<String> IMAGE_NAMES = Arrays.asList(
        "icon (1).png", "icon (2).png", "icon (3).png", "icon (4).png",
        "icon (5).png", "icon (6).png", "icon (7).png", "icon (8).png",
        "icon (9).png", "icon (10).png", "icon (11).png", "icon (12).png",
        "icon (13).png", "icon (14).png", "icon (15).png", "icon (16).png"
    );

    public GameRoom() {
        this.players = new ArrayList<>();
        this.readyPlayers = new ArrayList<>();
        this.rematchStatus = new ConcurrentHashMap<>();
    }

    public GameRoom(String roomId, Player host, int cardCount) {
        this.roomId = roomId;
        this.host = host;
        this.cardCount = cardCount;
        this.maxPlayers = 2;
        this.status = "WAITING";
        this.players = new ArrayList<>();
        this.players.add(host);
        this.readyPlayers = new ArrayList<>();
        this.readyPlayers.add(host.getUsername());
        this.rematchStatus = new ConcurrentHashMap<>();
    }
    
    public void initializeGame() {
        List<String> cards = new ArrayList<>();
        int numPairs = cardCount / 2;
        
        for (int i = 0; i < numPairs; i++) {
            String imageName = IMAGE_NAMES.get(i % IMAGE_NAMES.size());
            cards.add(imageName);
            cards.add(imageName);
        }
        Collections.shuffle(cards);
        
        ConcurrentHashMap<String, Integer> scores = new ConcurrentHashMap<>();
        for(Player p : players) {
            scores.put(p.getUsername(), 0);
            rematchStatus.put(p.getUsername(), false);
        }

        this.gameState = new GameState(roomId, cards, scores, cardCount);
        this.gameState.setCurrentPlayerUsername(host.getUsername());
        this.gameState.setGameStatus("PLAYING");
        this.gameState.setMessage("GO! Turn: " + host.getUsername());
        this.status = "PLAYING";
    }
    
    public void resetForRematch() {
        this.status = "WAITING";
        this.gameState = null;
        this.rematchStatus.clear();
        this.readyPlayers.clear();
        this.readyPlayers.add(host.getUsername());
    }
    
    public void setPlayerReady(String username) {
        if (!readyPlayers.contains(username)) {
            readyPlayers.add(username);
        }
    }
    
    public ConcurrentHashMap<String, Boolean> getRematchStatus() {
        return rematchStatus;
    }

    public boolean areAllPlayersReady() {
        if (players.size() == 1 && players.get(0).equals(host)) {
            return true;
        }
        return readyPlayers.size() == players.size();
    }
    
    public List<String> getReadyPlayers() {
        return readyPlayers;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public boolean addPlayer(Player player) {
        if (players.size() < maxPlayers && !players.contains(player)) {
            players.add(player);
            return true;
        }
        return false;
    }

    public void removePlayer(Player player) {
        players.remove(player);
        readyPlayers.remove(player.getUsername());
        rematchStatus.remove(player.getUsername());
    }

    public String getRoomId() {
        return roomId;
    }

    public Player getHost() {
        return host;
    }

    public void setHost(Player host) {
        this.host = host;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public int getCardCount() {
        return cardCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPlayerCount() {
        return players.size();
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GameRoom gameRoom = (GameRoom) obj;
        return roomId.equals(gameRoom.roomId);
    }

    @Override
    public int hashCode() {
        return roomId.hashCode();
    }

    @Override
    public String toString() {
        return "GameRoom{" +
                "roomId='" + roomId + '\'' +
                ", playerCount=" + players.size() +
                ", players=" + players +
                ", status='" + status + '\'' +
                '}';
    }
}