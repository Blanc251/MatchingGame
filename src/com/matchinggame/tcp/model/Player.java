package com.matchinggame.tcp.model;

import java.io.Serializable;
import java.util.Objects;

public class Player implements Serializable {
    private static final long serialVersionUID = 3L; 

    private String username;
    private int totalScore;
    private String status;

    public Player(String username, int totalScore, String status) {
        this.username = username;
        this.totalScore = totalScore;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Player player = (Player) obj;
        
        if (username == null) {
            return player.username == null;
        }
        
        return username.equalsIgnoreCase(player.username);
    }

    @Override
    public int hashCode() {
        
        return (username != null) ? username.toLowerCase().hashCode() : 0;
    }
}