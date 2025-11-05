package com.matchinggame.tcp.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class GameState implements Serializable {
    private static final long serialVersionUID = 11L;

    private String roomId;
    private List<String> cardValues;
    private boolean[] cardFlipped;
    private boolean[] cardMatched;
    
    private Map<String, Integer> scores;
    private String currentPlayerUsername;
    private String gameStatus;
    private String message;

    public GameState(String roomId, List<String> cardValues, Map<String, Integer> scores, int cardCount) {
        this.roomId = roomId;
        this.cardValues = cardValues;
        this.scores = scores;
        this.cardFlipped = new boolean[cardCount];
        this.cardMatched = new boolean[cardCount];
        this.gameStatus = "WAITING";
    }

    public String getRoomId() {
        return roomId;
    }

    public List<String> getCardValues() {
        return cardValues;
    }

    public boolean[] isCardFlipped() {
        return cardFlipped;
    }

    public boolean[] isCardMatched() {
        return cardMatched;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public String getCurrentPlayerUsername() {
        return currentPlayerUsername;
    }

    public String getGameStatus() {
        return gameStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setCardFlipped(int index, boolean state) {
        this.cardFlipped[index] = state;
    }

    public void setCardMatched(int index, boolean state) {
        this.cardMatched[index] = state;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }

    public void setCurrentPlayerUsername(String currentPlayerUsername) {
        this.currentPlayerUsername = currentPlayerUsername;
    }

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}