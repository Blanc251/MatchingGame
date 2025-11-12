package com.matchinggame.tcp.control;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import com.matchinggame.tcp.model.Command;
import com.matchinggame.tcp.model.Command.Type;
import com.matchinggame.tcp.model.FlipData;
import com.matchinggame.tcp.model.InviteData;
import com.matchinggame.tcp.model.Player;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private ServerControl serverControl;
    private Player player;
    private volatile boolean isClosing = false;

    public ClientHandler(Socket socket, ServerControl serverControl) {
        this.clientSocket = socket;
        this.serverControl = serverControl;
        try {
            oos = new ObjectOutputStream(clientSocket.getOutputStream());
            ois = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection();
        }
    }

    @Override
    public void run() {
        try {
            while (clientSocket.isConnected() && !isClosing) {
                Command command = (Command) ois.readObject();
                processCommand(command);
            }
        } catch (IOException e) {
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private void processCommand(Command command) {
        if (command.getType() == Type.LOGIN) {
            String tempUsername = (String) command.getData();
            
            if (serverControl.isUserLoggedIn(tempUsername)) {
                String errorMsg = "LOGIN ERROR: " + clientSocket.getInetAddress().getHostAddress() + " - Username already exists: " + tempUsername;
                serverControl.logError(errorMsg); 
                sendMessage(new Command(Type.LOGIN, "SERVER", "Error: Account " + tempUsername + " is already logged in."));
                closeConnection();
                return;
            }
            
            this.player = serverControl.findOrCreatePlayer(tempUsername);
            serverControl.addPlayer(this.player); 

            ArrayList<Player> playerList = serverControl.getOnlinePlayers();
            sendMessage(new Command(Type.LOGIN_SUCCESS, "SERVER", playerList));
            
            serverControl.broadcastPlayerList();
            return;
        } 
        
        if (player == null) {
            sendMessage(new Command(Type.LOGIN, "SERVER", "Error: Please login first."));
            closeConnection();
            return;
        }

        switch (command.getType()) {
            case CREATE_ROOM:
                int cardCount = (Integer) command.getData();
                serverControl.handleCreateRoom(this, cardCount);
                break;
                
            case CREATE_ROOM_AND_INVITE:
                InviteData inviteData = (InviteData) command.getData();
                serverControl.handleCreateRoomAndInvite(this, inviteData);
                break;

            case DECLINE_INVITE:
                String roomId = (String) command.getData();
                serverControl.handleDeclineInvite(this, roomId);
                break;
                
            case INVITE_PLAYER:
                String targetUsername = (String) command.getData();
                serverControl.handleInvitePlayer(this, targetUsername);
                break;
            case ACCEPT_INVITE:
                String roomIdToJoin = (String) command.getData();
                serverControl.handleJoinRoom(this, roomIdToJoin);
                break;
            case JOIN_ROOM:
                String roomId_Join = (String) command.getData();
                serverControl.handleJoinRoom(this, roomId_Join);
                break;
            case LEAVE_ROOM:
                String roomToLeave = (String) command.getData();
                serverControl.handleLeaveRoom(this, roomToLeave);
                break;
            case CHAT_MESSAGE: 
                String messageContent = (String) command.getData();
                break;
                
            case PLAYER_READY:
                serverControl.handlePlayerReady(this);
                break;
                
            case START_GAME:
                serverControl.handleStartGame(this);
                break;
                
            case FLIP_CARD:
                FlipData flipData = (FlipData) command.getData();
                serverControl.handleFlipCard(this, flipData);
                break;
            
            case QUIT_GAME:
                serverControl.handleQuitGame(this);
                break;
            
            case REMATCH_REQUEST:
                serverControl.handleRematchRequest(this);
                break;
            
            case REMATCH_RESPONSE:
                boolean accepted = (Boolean) command.getData();
                serverControl.handleRematchResponse(this, accepted);
                break;
                
            default:
                serverControl.logError("Received unknown command from " + player.getUsername() + ": " + command.getType());
                break;
        }
    }

    public void sendMessage(Object object) {
        if (isClosing) return;
        try {
            oos.reset();
            oos.writeObject(object);
            oos.flush();
        } catch (IOException e) {
            serverControl.logError("Failed to send message to " + (player != null ? player.getUsername() : "unauthenticated client"));
            closeConnection();
        }
    }

    public void closeConnection() {
        if (isClosing) return;
        isClosing = true;
        serverControl.removeHandler(this); 
        try {
            if (ois != null) ois.close();
            if (oos != null) oos.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Player getPlayer() {
        return player;
    }
}