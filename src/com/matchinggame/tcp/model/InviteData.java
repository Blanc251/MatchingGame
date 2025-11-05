package com.matchinggame.tcp.model;

import java.io.Serializable;

public class InviteData implements Serializable {
    private static final long serialVersionUID = 20L;
    private int cardCount;
    private String targetUsername;

    public InviteData(int cardCount, String targetUsername) {
        this.cardCount = cardCount;
        this.targetUsername = targetUsername;
    }

    public int getCardCount() {
        return cardCount;
    }

    public String getTargetUsername() {
        return targetUsername;
    }
}