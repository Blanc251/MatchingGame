package com.matchinggame.tcp.model;

import java.io.Serializable;

public class Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN,
        LOGIN_SUCCESS,
        UPDATE_PLAYER_LIST,
        CHAT_MESSAGE,
        GAME_STATE_UPDATE,

        CREATE_ROOM,
        CREATE_ROOM_SUCCESS,
        JOIN_ROOM,
        INVITE_PLAYER,
        RECEIVE_INVITE,
        ACCEPT_INVITE,
        JOIN_ROOM_SUCCESS,
        JOIN_ROOM_FAILED,
        UPDATE_ROOM_LIST,
        UPDATE_ROOM_STATE,
        LEAVE_ROOM,
        
        CREATE_ROOM_AND_INVITE,
        DECLINE_INVITE,
        RECEIVE_INVITE_DECLINED,

        PLAYER_READY,
        START_GAME,
        GAME_STARTED,
        FLIP_CARD,
        GAME_UPDATE,
        GAME_OVER,
        
        QUIT_GAME,
        REMATCH_REQUEST,
        REMATCH_RESPONSE,
        UPDATE_PLAYER_SCORE
    }

    private Type type;
    private String username;
    private Object data;

    public Command(Type type, String username, Object data) {
        this.type = type;
        this.username = username;
        this.data = data;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}