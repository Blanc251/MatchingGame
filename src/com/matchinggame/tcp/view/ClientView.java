package com.matchinggame.tcp.view;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import com.matchinggame.tcp.control.ClientControl;
import com.matchinggame.tcp.model.Command;
import com.matchinggame.tcp.model.FlipData;
import com.matchinggame.tcp.model.GameRoom;
import com.matchinggame.tcp.model.GameState;
import com.matchinggame.tcp.model.InviteData;
import com.matchinggame.tcp.model.Player;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

public class ClientView extends JFrame {
    
    private ClientControl clientControl;
    private String currentUsername = "Guest";
    private GameRoom currentRoom = null;
    private GameState currentGameState = null;

    private CardLayout mainLayout;
    private JPanel mainPanel;
    
    private JPanel loginPanel;
    private JTextField usernameField;
    private JButton connectButton;
    
    private JPanel lobbyPanel;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;
    private JButton createRoomButton;
    
    private JPopupMenu playerContextMenu;
    private JMenuItem inviteMenuItem;

    private JPanel gamePanel;
    private JLabel roomNameLabel;
    private JList<String> roomPlayerList;
    private DefaultListModel<String> roomPlayerListModel;
    private JButton readyButton;
    private JButton startGameButton;
    private JButton leaveRoomButton;
    
    private JPanel gameBoardPanel;
    private List<JButton> cardButtons = new ArrayList<>();
    private JLabel player1ScoreLabel;
    private JLabel player2ScoreLabel;
    private JLabel turnStatusLabel;
    
    private JPanel gameControlPanel;
    private JButton quitGameButton;
    
    private JLabel countdownLabel;

    public ClientView() {
        super("Game Lobby");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);

        clientControl = new ClientControl(this);

        mainLayout = new CardLayout();
        mainPanel = new JPanel(mainLayout);

        initLoginPanel();
        initLobbyPanel();
        initGamePanel(); 

        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(gamePanel, "GAME");

        add(mainPanel);
        
        mainLayout.show(mainPanel, "LOGIN");
        setVisible(true);
    }
    
    private void initLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout());
        JPanel centerPanel = new JPanel(new FlowLayout());
        
        usernameField = new JTextField(10);
        usernameField.setText("User" + (new java.util.Random().nextInt(100)));
        connectButton = new JButton("Connect & Login");
        connectButton.addActionListener(new ConnectListener());
        
        centerPanel.add(new JLabel("Username:"));
        centerPanel.add(usernameField);
        centerPanel.add(connectButton);
        loginPanel.add(centerPanel);
    }

    private void initLobbyPanel() {
        lobbyPanel = new JPanel(new BorderLayout());
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        JPanel playerSection = new JPanel(new BorderLayout());
        playerSection.setBorder(new TitledBorder("Online Players"));
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        
        createPlayerContextMenu();
        playerList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = playerList.locationToIndex(e.getPoint());
                    if (index > -1) {
                        playerList.setSelectedIndex(index);
                        playerContextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
        
        playerSection.add(new JScrollPane(playerList), BorderLayout.CENTER);
        
        JPanel roomSection = new JPanel(new BorderLayout());
        roomSection.setBorder(new TitledBorder("Available Rooms"));
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    joinSelectedRoom();
                }
            }
        });
        roomSection.add(new JScrollPane(roomList), BorderLayout.CENTER);
        createRoomButton = new JButton("Tạo phòng (Tự chơi)");
        createRoomButton.addActionListener(e -> showCreateRoomDialog(null));
        roomSection.add(createRoomButton, BorderLayout.SOUTH);

        splitPane.setLeftComponent(playerSection);
        splitPane.setRightComponent(roomSection);
        
        lobbyPanel.add(splitPane, BorderLayout.CENTER);
    }
    
    private void createPlayerContextMenu() {
        playerContextMenu = new JPopupMenu();
        inviteMenuItem = new JMenuItem("Mời chơi");
        inviteMenuItem.addActionListener(e -> {
            String targetUsername = playerList.getSelectedValue();
            if (targetUsername != null && !targetUsername.isEmpty()) {
                if(targetUsername.endsWith(" (Me)")) {
                    targetUsername = targetUsername.substring(0, targetUsername.length() - 5);
                }
                showCreateRoomDialog(targetUsername);
            }
        });
        playerContextMenu.add(inviteMenuItem);
    }
    
    private void initGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        
        JPanel roomInfoPanel = new JPanel(new BorderLayout());
        roomInfoPanel.setBorder(new TitledBorder("Phòng chờ"));

        roomNameLabel = new JLabel("Phòng: ");
        roomInfoPanel.add(roomNameLabel, BorderLayout.NORTH);
        
        roomPlayerListModel = new DefaultListModel<>();
        roomPlayerList = new JList<>(roomPlayerListModel);
        roomInfoPanel.add(new JScrollPane(roomPlayerList), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        readyButton = new JButton("Sẵn sàng");
        startGameButton = new JButton("Bắt đầu chơi");
        leaveRoomButton = new JButton("Rời phòng");
        
        readyButton.addActionListener(e -> clientControl.sendCommand(new Command(Command.Type.PLAYER_READY, currentUsername, currentRoom.getRoomId())));
        startGameButton.addActionListener(e -> clientControl.sendCommand(new Command(Command.Type.START_GAME, currentUsername, currentRoom.getRoomId())));
        leaveRoomButton.addActionListener(e -> leaveCurrentRoom());
        
        buttonPanel.add(readyButton);
        buttonPanel.add(startGameButton);
        buttonPanel.add(leaveRoomButton);
        roomInfoPanel.add(buttonPanel, BorderLayout.SOUTH);
        roomInfoPanel.setPreferredSize(new Dimension(200, 0));
        
        gamePanel.add(roomInfoPanel, BorderLayout.WEST);

        JPanel centerGamePanel = new JPanel(new BorderLayout());
        
        gameBoardPanel = new JPanel();
        centerGamePanel.add(gameBoardPanel, BorderLayout.CENTER);
        
        JPanel topGamePanel = new JPanel(new GridLayout(1, 3));
        player1ScoreLabel = new JLabel("Player 1: 0");
        player2ScoreLabel = new JLabel("Player 2: 0");
        turnStatusLabel = new JLabel("Đang chờ...");
        countdownLabel = new JLabel("Sẵn sàng...");
        
        player1ScoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
        player2ScoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
        turnStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        topGamePanel.add(player1ScoreLabel);
        topGamePanel.add(turnStatusLabel);
        topGamePanel.add(player2ScoreLabel);
        
        centerGamePanel.add(topGamePanel, BorderLayout.NORTH);
        
        // --- KHỞI TẠO VÀ SỬ DỤNG gameControlPanel ĐÚNG CÁCH ---
        
        // Khởi tạo gameControlPanel (Fix NPE)
        gameControlPanel = new JPanel(new FlowLayout());
        quitGameButton = new JButton("Thoát");
        quitGameButton.addActionListener(e -> quitGame());
        gameControlPanel.add(quitGameButton);
        
        // Tạo panel chứa cả Countdown và GameControlPanel ở phía South
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(countdownLabel, BorderLayout.NORTH);
        southPanel.add(gameControlPanel, BorderLayout.CENTER);

        centerGamePanel.add(southPanel, BorderLayout.SOUTH);
        // -----------------------------------------------------------
        
        gamePanel.add(centerGamePanel, BorderLayout.CENTER);
    }
    
    private void showLobbyView() {
        currentRoom = null;
        currentGameState = null;
        mainLayout.show(mainPanel, "LOBBY");
        setTitle("Sảnh chờ - Chào, " + currentUsername);
    }
    
    private void showGameRoomView(GameRoom room) {
        currentRoom = room;
        mainLayout.show(mainPanel, "GAME");
        updateRoomState(room);
    }
    
    public void handleServerCommand(Command command) {
        System.out.println("[CLIENT " + currentUsername + "] Received command: " + command.getType());
        
        switch (command.getType()) {
            case LOGIN_SUCCESS:
                ArrayList<Player> players = (ArrayList<Player>) command.getData();
                updatePlayerList(players);
                showLobbyView();
                break;
                
            case UPDATE_PLAYER_LIST:
                ArrayList<Player> lobbyPlayers = (ArrayList<Player>) command.getData();
                updatePlayerList(lobbyPlayers);
                break;
                
            case UPDATE_PLAYER_SCORE:
                List<Player> leaderboard = (List<Player>) command.getData();
                updateLeaderboard(leaderboard); 
                break;
                
            case UPDATE_ROOM_LIST:
                ArrayList<GameRoom> rooms = (ArrayList<GameRoom>) command.getData();
                updateRoomList(rooms);
                break;
                
            case LOGIN:
                String response = (String) command.getData();
                if (response.startsWith("Lỗi")) {
                    JOptionPane.showMessageDialog(this, response, "Lỗi Đăng nhập", JOptionPane.ERROR_MESSAGE);
                    resetLoginControls();
                }
                break;
                
            case RECEIVE_INVITE:
                String inviter = command.getUsername();
                String roomId = (String) command.getData();
                int choice = JOptionPane.showConfirmDialog(this, 
                        inviter + " mời bạn vào phòng " + roomId + ". Bạn có muốn tham gia?",
                        "Lời mời chơi", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    clientControl.sendCommand(new Command(Command.Type.ACCEPT_INVITE, currentUsername, roomId));
                } else {
                    clientControl.sendCommand(new Command(Command.Type.DECLINE_INVITE, currentUsername, roomId));
                }
                break;

          case RECEIVE_INVITE_DECLINED:
                String declineMsg = (String) command.getData();
                JOptionPane.showMessageDialog(this, declineMsg, "Từ chối lời mời", JOptionPane.INFORMATION_MESSAGE);
                break;
                
            case CREATE_ROOM_SUCCESS:
            case JOIN_ROOM_SUCCESS:
                GameRoom joinedRoom = (GameRoom) command.getData();
                showGameRoomView(joinedRoom);
                break;
                
            case UPDATE_ROOM_STATE:
                GameRoom updatedRoom = (GameRoom) command.getData();
                if (currentRoom != null && currentRoom.getRoomId().equals(updatedRoom.getRoomId())) {
                    System.out.println("Client " + currentUsername + " - Updating room state. Players received: " + updatedRoom.getPlayerCount() + ", Players: " + updatedRoom.getPlayers().stream().map(p -> p.getUsername()).collect(java.util.stream.Collectors.toList()));
                    updateRoomState(updatedRoom);
                }
                break;
                
            case GAME_STARTED:
            case GAME_UPDATE:
                currentGameState = (GameState) command.getData();
                renderGameBoard(currentGameState);
                break;
                
            case GAME_OVER:
                currentGameState = (GameState) command.getData();
                renderGameBoard(currentGameState);
                showGameOverDialog(currentGameState.getMessage());
                break;
                
            case JOIN_ROOM_FAILED:
                String errorMsg = (String) command.getData();
                JOptionPane.showMessageDialog(this, errorMsg, "Lỗi tham gia phòng", JOptionPane.ERROR_MESSAGE);
                break;
                
            case LEAVE_ROOM:
                String leaveMsg = (String) command.getData();
                JOptionPane.showMessageDialog(this, leaveMsg, "Rời phòng", JOptionPane.INFORMATION_MESSAGE);
                showLobbyView();
                break;
                
            case REMATCH_REQUEST:
                String rematcher = command.getUsername();
                if (currentRoom != null && currentRoom.getStatus().equals("FINISHED")) {
                    int rematchChoice = JOptionPane.showConfirmDialog(this, 
                            rematcher + " muốn chơi lại. Bạn có đồng ý?",
                            "Yêu cầu chơi lại", JOptionPane.YES_NO_OPTION);
                    clientControl.sendCommand(new Command(Command.Type.REMATCH_RESPONSE, currentUsername, rematchChoice == JOptionPane.YES_OPTION));
                }
                break;
                
            default:
                break;
        }
    }
    
    private void updateLeaderboard(List<Player> leaderboard) {
        if (!leaderboard.isEmpty()) {
            Player topPlayer = leaderboard.get(0);
            System.out.println("Leader: " + topPlayer.getUsername() + ", Score: " + topPlayer.getTotalScore() + ", Wins: " + topPlayer.getTotalWins());
        }
    }
    
    private void showGameOverDialog(String message) {
        int choice = JOptionPane.showConfirmDialog(this, 
                message + "\n\nChơi lại?",
                "Ván chơi kết thúc", 
                JOptionPane.YES_NO_OPTION);
        
        if (choice == JOptionPane.YES_OPTION) {
            clientControl.sendCommand(new Command(Command.Type.REMATCH_REQUEST, currentUsername, null));
        } else {
            leaveCurrentRoom();
        }
    }
    
    private void updatePlayerList(ArrayList<Player> players) {
        playerListModel.clear();
        for (Player p : players) {
            if (p.getUsername().equalsIgnoreCase(currentUsername)) {
                 playerListModel.addElement(p.getUsername() + " (Me)");
            } else {
                 playerListModel.addElement(p.getUsername());
            }
        }
    }
    
    private void updateRoomList(ArrayList<GameRoom> rooms) {
        roomListModel.clear();
        for (GameRoom room : rooms) {
            String displayText = String.format("%s (%d/%d) - %d thẻ - %s", 
                                                room.getRoomId(),
                                                room.getPlayerCount(),
                                                room.getMaxPlayers(),
                                                room.getCardCount(),
                                                room.getStatus());
            roomListModel.addElement(displayText);
        }
    }
    
    private void updateRoomState(GameRoom room) {
    currentRoom = room;
    setTitle("Phòng: " + room.getRoomId() + " - User: " + currentUsername);
    roomNameLabel.setText("Phòng: " + room.getRoomId() + " (" + room.getCardCount() + " thẻ)");
    
    System.out.println("[CLIENT " + currentUsername + "] updateRoomState called");
    System.out.println("[CLIENT " + currentUsername + "] Room: " + room.getRoomId());
    System.out.println("[CLIENT " + currentUsername + "] Players count: " + room.getPlayerCount());
    System.out.println("[CLIENT " + currentUsername + "] Players list: " + 
                      room.getPlayers().stream().map(Player::getUsername).collect(java.util.stream.Collectors.toList()));
    System.out.println("[CLIENT " + currentUsername + "] Ready players: " + room.getReadyPlayers());
    
    roomPlayerListModel.clear();
    boolean isHost = currentUsername.equalsIgnoreCase(room.getHost().getUsername());
    
    for (Player p : room.getPlayers()) {
        String playerText = p.getUsername();
        
        boolean isCurrentPlayerHost = p.getUsername().equalsIgnoreCase(room.getHost().getUsername());
        
        if (isCurrentPlayerHost) {
            playerText += " (Host) - Sẵn sàng";
        } else {
            if (room.getReadyPlayers().stream().anyMatch(name -> name.equalsIgnoreCase(p.getUsername()))) {
                playerText += " - Sẵn sàng";
            } else {
                playerText += " - Chưa sẵn sàng";
            }
        }
        
        System.out.println("[CLIENT " + currentUsername + "] Adding: " + playerText);
        roomPlayerListModel.addElement(playerText);
    }
    
    readyButton.setVisible(!isHost);
    startGameButton.setVisible(isHost);
    
    if (!isHost) {
        if(room.getReadyPlayers().stream().anyMatch(name -> name.equalsIgnoreCase(currentUsername))) {
            readyButton.setEnabled(false);
            readyButton.setText("Đã sẵn sàng");
        } else {
            readyButton.setEnabled(true);
            readyButton.setText("Sẵn sàng");
        }
    }
    
    startGameButton.setEnabled(room.areAllPlayersReady() && room.getPlayerCount() > 1);
}

    private void renderGameBoard(GameState state) {
        if (state == null) return;
        
        currentGameState = state;
        gameBoardPanel.removeAll();
        
        int cardCount = state.getCardValues().size();
        int gridSize = (int) Math.ceil(Math.sqrt(cardCount));
        gameBoardPanel.setLayout(new GridLayout(gridSize, gridSize, 5, 5));
        
        if (cardButtons.size() != cardCount) {
            cardButtons.clear();
            for (int i = 0; i < cardCount; i++) {
                JButton cardButton = new JButton("?");
                cardButton.setFont(new Font("Arial", Font.BOLD, 24));
                final int index = i;
                cardButton.addActionListener(e -> onCardFlipped(index));
                cardButtons.add(cardButton);
            }
        }
        
        boolean myTurn = state.getCurrentPlayerUsername().equalsIgnoreCase(currentUsername);
        
        turnStatusLabel.setText("Lượt của: " + state.getCurrentPlayerUsername());
        turnStatusLabel.setForeground(myTurn ? Color.BLUE : Color.BLACK);
        countdownLabel.setText(state.getMessage());
        
        if (!state.getGameStatus().equals("PLAYING")) {
            countdownLabel.setText("Trò chơi đã kết thúc: " + state.getMessage());
        } else if (myTurn) {
             countdownLabel.setText("LƯỢT CỦA BẠN! " + state.getMessage());
        } else {
             countdownLabel.setText("Đang chờ đối thủ... " + state.getMessage());
        }

        Map<String, Integer> scores = state.getScores();
        List<Player> playersInRoom = currentRoom.getPlayers();
        
        if(playersInRoom.size() > 0) {
            String p1Name = playersInRoom.get(0).getUsername();
            player1ScoreLabel.setText(p1Name + ": " + scores.getOrDefault(p1Name, 0));
        }
         if(playersInRoom.size() > 1) {
            String p2Name = playersInRoom.get(1).getUsername();
            player2ScoreLabel.setText(p2Name + ": " + scores.getOrDefault(p2Name, 0));
        }

        for (int j = 0; j < cardCount; j++) {
            JButton button = cardButtons.get(j);
            if (state.isCardMatched()[j]) {
                button.setVisible(false);
            } else {
                button.setVisible(true);
                if (state.isCardFlipped()[j]) {
                    button.setText(state.getCardValues().get(j));
                    button.setEnabled(false);
                } else {
                    button.setText("?");
                    button.setEnabled(myTurn && state.getGameStatus().equals("PLAYING"));
                }
            }
            gameBoardPanel.add(button);
        }
        
        readyButton.setVisible(false);
        startGameButton.setVisible(false);
        
        gameControlPanel.setVisible(state.getGameStatus().equals("PLAYING") || state.getGameStatus().equals("FINISHED"));
        
        gameBoardPanel.revalidate();
        gameBoardPanel.repaint();
    }
    
    private void onCardFlipped(int cardIndex) {
        if (currentGameState != null && currentGameState.getGameStatus().equals("PLAYING")) {
            FlipData flipData = new FlipData(currentRoom.getRoomId(), cardIndex);
            clientControl.sendCommand(new Command(Command.Type.FLIP_CARD, currentUsername, flipData));
        }
    }

    private void showCreateRoomDialog(String targetUsername) {
        JTextField cardCountField = new JTextField("16");
        final JComponent[] inputs = new JComponent[] {
                new JLabel("Số lượng thẻ (chẵn, 16-36):"),
                cardCountField
        };
        
        String dialogTitle = (targetUsername == null) ? "Tạo phòng mới" : "Mời " + targetUsername + " vào phòng";
        
        int result = JOptionPane.showConfirmDialog(this, inputs, dialogTitle, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            try {
                int cardCount = Integer.parseInt(cardCountField.getText());
                if (cardCount < 16 || cardCount > 36 || cardCount % 2 != 0) {
                    throw new NumberFormatException();
                }
                
                if (targetUsername == null) {
                    clientControl.sendCommand(new Command(Command.Type.CREATE_ROOM, currentUsername, cardCount));
                } else {
                    InviteData inviteData = new InviteData(cardCount, targetUsername);
                    clientControl.sendCommand(new Command(Command.Type.CREATE_ROOM_AND_INVITE, currentUsername, inviteData));
                }
                
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập số thẻ hợp lệ (số chẵn từ 16 đến 36).", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void joinSelectedRoom() {
        String selectedRoom = roomList.getSelectedValue();
        if (selectedRoom == null || selectedRoom.isEmpty()) {
            return;
        }
        String roomId = selectedRoom.split(" ")[0];
        clientControl.sendCommand(new Command(Command.Type.JOIN_ROOM, currentUsername, roomId));
    }

    private void leaveCurrentRoom() {
        if (currentRoom != null) {
            clientControl.sendCommand(new Command(Command.Type.LEAVE_ROOM, currentUsername, currentRoom.getRoomId()));
            gameBoardPanel.removeAll();
            cardButtons.clear();
            showLobbyView();
        }
    }
    
    private void quitGame() {
        if (currentRoom != null && currentRoom.getStatus().equals("PLAYING")) {
             int choice = JOptionPane.showConfirmDialog(this, 
                    "Bạn có chắc muốn thoát? Bạn sẽ bị tính thua.",
                    "Thoát trò chơi", JOptionPane.YES_NO_OPTION);
             if (choice == JOptionPane.YES_OPTION) {
                 clientControl.sendCommand(new Command(Command.Type.QUIT_GAME, currentUsername, currentRoom.getRoomId()));
             }
        } else {
            leaveCurrentRoom();
        }
    }

    public void resetLoginControls() {
        connectButton.setEnabled(true);
        usernameField.setEditable(true);
    }
    
    public void logError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        resetLoginControls();
        mainLayout.show(mainPanel, "LOGIN");
        setTitle("Game Lobby");
    }

    private class ConnectListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            currentUsername = usernameField.getText();
            if (currentUsername.trim().isEmpty()) {
                JOptionPane.showMessageDialog(ClientView.this, "Username không được để trống.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            connectButton.setEnabled(false);
            usernameField.setEditable(false);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean connected = false;
                    if (clientControl.getSocket() == null || clientControl.getSocket().isClosed()) {
                        connected = clientControl.connect();
                        if (!connected) {
                            SwingUtilities.invokeLater(() -> resetLoginControls());
                            return; 
                        }
                    }
                    
                    Command loginCommand = new Command(Command.Type.LOGIN, currentUsername, currentUsername);
                    clientControl.sendCommand(loginCommand);
                }
            }).start();
        }
    }
}