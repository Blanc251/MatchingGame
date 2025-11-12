package com.matchinggame.tcp.model;

import java.io.Serializable;
import java.util.Date;

public class MatchHistoryEntry implements Serializable {
    private static final long serialVersionUID = 21L;

    private String opponentName;
    private int myScore;
    private int opponentScore;
    private String result;
    private Date playedOn;

    public MatchHistoryEntry(String opponentName, int myScore, int opponentScore, String result, Date playedOn) {
        this.opponentName = opponentName;
        this.myScore = myScore;
        this.opponentScore = opponentScore;
        this.result = result;
        this.playedOn = playedOn;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public int getMyScore() {
        return myScore;
    }

    public int getOpponentScore() {
        return opponentScore;
    }

    public String getResult() {
        return result;
    }

    public Date getPlayedOn() {
        return playedOn;
    }
}