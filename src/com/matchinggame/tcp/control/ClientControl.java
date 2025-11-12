package com.matchinggame.tcp.control;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import com.matchinggame.tcp.model.Command;
import com.matchinggame.tcp.view.ClientView;
import javax.swing.SwingUtilities;

public class ClientControl {
    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private ClientView clientView;

    private final String SERVER_IP = "172.11.222.93"; 
    private final int PORT = 9999;

    public ClientControl(ClientView view) {
        this.clientView = view;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean connect() {
        try {
            socket = new Socket(SERVER_IP, PORT);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            new ServerListener().start();
            return true;
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> 
                clientView.logError("Connection Error: Could not connect to the server.")
            );
            return false;
        }
    }

    public void sendCommand(Command command) {
        if (oos == null) return;
        try {
            oos.writeObject(command);
            oos.flush();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> 
                clientView.logError("Send Error: Connection lost.")
            );
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (ois != null) ois.close();
            if (oos != null) oos.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ServerListener extends Thread {
        @Override
        public void run() {
            try {
                while (socket.isConnected()) {
                    Command receivedCommand = (Command) ois.readObject();
                    
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            clientView.handleServerCommand(receivedCommand);
                        }
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> 
                    clientView.logError("Server has closed the connection.")
                );
            } catch (ClassNotFoundException e) {
                SwingUtilities.invokeLater(() -> 
                    clientView.logError("Error receiving Command object.")
                );
            } finally {
                closeConnection();
            }
        }
    }
}