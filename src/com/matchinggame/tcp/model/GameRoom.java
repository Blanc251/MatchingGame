package com.matchinggame.tcp.model;

import java.io.Serializable;
import java.util.ArrayList;
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
    
    private List<String> readyPlayers;
    private GameState gameState;

    // ✅ Constructor không tham số cho deserialization
    public GameRoom() {
        this.players = new ArrayList<>();
        this.readyPlayers = new ArrayList<>();
    }

    public GameRoom(String roomId, Player host, int cardCount) {
        this.roomId = roomId;
        this.host = host;
        this.cardCount = cardCount;
        this.maxPlayers = 2;
        this.status = "WAITING";
        // ✅ Dùng ArrayList thay vì Collections.synchronizedList
        this.players = new ArrayList<>();
        this.players.add(host);
        this.readyPlayers = new ArrayList<>();
    }
    
    public void initializeGame() {
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < cardCount / 2; i++) {
            cards.add(String.valueOf(i));
            cards.add(String.valueOf(i));
        }
        Collections.shuffle(cards);
        
        ConcurrentHashMap<String, Integer> scores = new ConcurrentHashMap<>();
        for(Player p : players) {
            scores.put(p.getUsername(), 0);
        }

        this.gameState = new GameState(roomId, cards, scores, cardCount);
        this.gameState.setCurrentPlayerUsername(host.getUsername());
        this.gameState.setGameStatus("PLAYING");
        this.gameState.setMessage("Trò chơi bắt đầu! Lượt của " + host.getUsername());
        this.status = "PLAYING";
    }
    
    public void setPlayerReady(String username) {
        if (!readyPlayers.contains(username)) {
            readyPlayers.add(username);
        }
    }

    public boolean areAllPlayersReady() {
        return readyPlayers.size() == players.size();
    }
    
    public List<String> getReadyPlayers() {
        return readyPlayers;
    }

    public GameState getGameState() {
        return gameState;
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
    }

    public String getRoomId() {
        return roomId;
    }

    public Player getHost() {
        return host;
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