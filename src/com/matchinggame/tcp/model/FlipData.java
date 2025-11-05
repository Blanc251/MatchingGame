package com.matchinggame.tcp.model;

import java.io.Serializable;

public class FlipData implements Serializable {
    private static final long serialVersionUID = 12L;
    
    private String roomId;
    private int cardIndex;

    public FlipData(String roomId, int cardIndex) {
        this.roomId = roomId;
        this.cardIndex = cardIndex;
    }

    public String getRoomId() {
        return roomId;
    }

    public int getCardIndex() {
        return cardIndex;
    }
}