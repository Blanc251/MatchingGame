package com.matchinggame.tcp.view;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.matchinggame.tcp.control.ClientControl;
import com.matchinggame.tcp.model.Command;
import com.matchinggame.tcp.model.FlipData;
import com.matchinggame.tcp.model.GameRoom;
import com.matchinggame.tcp.model.GameState;
import com.matchinggame.tcp.model.InviteData;
import com.matchinggame.tcp.model.MatchHistoryEntry;
import com.matchinggame.tcp.model.Player;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
    private JList<Player> playerList;
    private DefaultListModel<Player> playerListModel;
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;
    private JButton createRoomButton;
    
    private JList<Player> leaderboardList;
    private DefaultListModel<Player> leaderboardListModel;
    private JButton viewHistoryButton;
    private MatchHistoryDialog matchHistoryDialog;
    
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
    private JPanel topGamePanel;
    private JButton quitGameButton;
    
    private JLabel countdownLabel;
    
    private Map<String, ImageIcon> imageCache;
    
    private JProgressBar turnTimerBar;
    private javax.swing.Timer swingTurnTimer;
    private javax.swing.Timer swingPrepareTimer;
    private int turnTimeRemaining;
    private int prepareTimeRemaining;
    private static final int TURN_DURATION_SEC = 10;
    private static final int PREPARE_DURATION = 2;
    private int clientFlipCount = 0;

    private static final Color CHARCOAL_BLUE = new Color(0x1a202c);
    private static final Color GREY_BLUE = new Color(0x2d3748);
    private static final Color OFF_WHITE = new Color(0xf7fafc);
    private static final Color LIGHT_GREY = new Color(0xa0aec0);
    private static final Color VIBRANT_TEAL = new Color(0x38b2ac);
    private static final Color WARM_ORANGE = new Color(0xf6ad55);
    private static final Color CLEAR_GREEN = new Color(0x48bb78);
    private static final Color SOFT_RED = new Color(0xf56565);

    private static final Font FONT_MAIN_BOLD = new Font("Comic Sans MS", Font.BOLD, 14);
    private static final Font FONT_MAIN_PLAIN = new Font("Comic Sans MS", Font.PLAIN, 14);
    
    public ClientView() {
        super("Matching Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
        
        getContentPane().setBackground(CHARCOAL_BLUE);

        clientControl = new ClientControl(this);
        loadImages(); 
        initTimer();
        matchHistoryDialog = new MatchHistoryDialog(this);

        mainLayout = new CardLayout();
        mainPanel = new JPanel(mainLayout);
        mainPanel.setBackground(CHARCOAL_BLUE);

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
    
    private void initTimer() {
        turnTimeRemaining = TURN_DURATION_SEC;
        
        swingTurnTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                turnTimeRemaining--;
                turnTimerBar.setValue(turnTimeRemaining);
                turnTimerBar.setString(turnTimeRemaining + "s");

                if (turnTimeRemaining <= 3) {
                    turnTimerBar.setForeground(SOFT_RED);
                }

                if (turnTimeRemaining <= 0) {
                    swingTurnTimer.stop();
                }
            }
        });
        
        prepareTimeRemaining = PREPARE_DURATION;
        swingPrepareTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                prepareTimeRemaining--;
                turnTimerBar.setValue(prepareTimeRemaining);
                turnTimerBar.setString(prepareTimeRemaining + "s");
                
                if (prepareTimeRemaining <= 0) {
                    swingPrepareTimer.stop();
                }
            }
        });
    }
    
    private void loadImages() {
        imageCache = new HashMap<>();
        
        for (int i = 1; i <= 16; i++) {
            String imageName = "icon (" + i + ").png";
            try {
                ImageIcon originalIcon = new ImageIcon(getClass().getResource("/com/matchinggame/resources/images/" + imageName));
                imageCache.put(imageName, originalIcon);
            } catch (Exception e) {
                System.err.println("Could not load " + imageName);
            }
        }
    }
    
    private ImageIcon scaleImage(ImageIcon icon, int width, int height) {
        if (icon == null) return null;
        Image img = icon.getImage();
        Image scaledImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImg);
    }
    
    private void initLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBackground(CHARCOAL_BLUE);
        
        JPanel centerPanel = new JPanel(new FlowLayout());
        centerPanel.setBackground(CHARCOAL_BLUE);
        
        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(LIGHT_GREY);
        userLabel.setFont(FONT_MAIN_PLAIN);
        
        usernameField = new JTextField(10);
        usernameField.setFont(FONT_MAIN_PLAIN.deriveFont(14f));
        usernameField.setBackground(GREY_BLUE);
        usernameField.setForeground(OFF_WHITE);
        usernameField.setBorder(BorderFactory.createLineBorder(LIGHT_GREY));
        usernameField.setCaretColor(OFF_WHITE);
        
        connectButton = new JButton("Connect & Login");
        styleButton(connectButton, VIBRANT_TEAL, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(12f));
        connectButton.addActionListener(new ConnectListener());
        
        centerPanel.add(userLabel);
        centerPanel.add(usernameField);
        centerPanel.add(connectButton);
        loginPanel.add(centerPanel);
    }

    private void initLobbyPanel() {
        lobbyPanel = new JPanel(new BorderLayout());
        lobbyPanel.setBackground(CHARCOAL_BLUE);
        
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setResizeWeight(0.7);
        mainSplitPane.setBackground(CHARCOAL_BLUE);
        mainSplitPane.setBorder(null);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(CHARCOAL_BLUE);
        
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        leftSplitPane.setResizeWeight(0.5);
        leftSplitPane.setBackground(CHARCOAL_BLUE);
        leftSplitPane.setBorder(null);
        
        JPanel playerSection = new JPanel(new BorderLayout());
        playerSection.setBackground(CHARCOAL_BLUE);
        playerSection.setBorder(createThemedTitledBorder("All Players"));
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        styleList(playerList);
        playerList.setCellRenderer(new PlayerListRenderer());
        
        createPlayerContextMenu();
        playerList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = playerList.locationToIndex(e.getPoint());
                    if (index > -1) {
                        playerList.setSelectedIndex(index);
                        Player selectedPlayer = playerList.getModel().getElementAt(index);
                        if (selectedPlayer != null && "Online".equalsIgnoreCase(selectedPlayer.getStatus())) {
                            inviteMenuItem.setEnabled(true);
                        } else {
                            inviteMenuItem.setEnabled(false);
                        }
                        playerContextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
        JScrollPane playerScrollPane = new JScrollPane(playerList);
        playerScrollPane.setBorder(null);
        playerSection.add(playerScrollPane, BorderLayout.CENTER);
        
        JPanel roomSection = new JPanel(new BorderLayout());
        roomSection.setBackground(CHARCOAL_BLUE);
        roomSection.setBorder(createThemedTitledBorder("Available Rooms"));
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        styleList(roomList);
        roomList.setCellRenderer(new RoomListRenderer());
        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    joinSelectedRoom();
                }
            }
        });
        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(null);
        roomSection.add(roomScrollPane, BorderLayout.CENTER);
        
        JPanel roomControlPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        roomControlPanel.setBackground(CHARCOAL_BLUE);
        
        createRoomButton = new JButton("Create Room (Solo)");
        styleButton(createRoomButton, VIBRANT_TEAL, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(18f));
        createRoomButton.addActionListener(e -> showCreateRoomDialog(null));
        
        viewHistoryButton = new JButton("View Match History");
        styleButton(viewHistoryButton, WARM_ORANGE, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(18f));
        viewHistoryButton.addActionListener(e -> showMatchHistory());
        
        roomControlPanel.add(createRoomButton);
        roomControlPanel.add(viewHistoryButton);
        roomSection.add(roomControlPanel, BorderLayout.SOUTH);
        
        leftSplitPane.setTopComponent(playerSection);
        leftSplitPane.setBottomComponent(roomSection);
        leftPanel.add(leftSplitPane, BorderLayout.CENTER);
        
        JPanel leaderboardSection = new JPanel(new BorderLayout());
        leaderboardSection.setBackground(CHARCOAL_BLUE);
        leaderboardSection.setBorder(createThemedTitledBorder("Leaderboard (Score | W-L-D)"));
        leaderboardListModel = new DefaultListModel<>();
        leaderboardList = new JList<>(leaderboardListModel);
        styleList(leaderboardList);
        leaderboardList.setCellRenderer(new LeaderboardListRenderer());
        JScrollPane leaderboardScrollPane = new JScrollPane(leaderboardList);
        leaderboardScrollPane.setBorder(null);
        leaderboardSection.add(leaderboardScrollPane, BorderLayout.CENTER);

        mainSplitPane.setLeftComponent(leftPanel);
        mainSplitPane.setRightComponent(leaderboardSection);
        
        lobbyPanel.add(mainSplitPane, BorderLayout.CENTER);
    }
    
    private void createPlayerContextMenu() {
        playerContextMenu = new JPopupMenu();
        playerContextMenu.setBackground(GREY_BLUE);
        playerContextMenu.setBorder(BorderFactory.createLineBorder(VIBRANT_TEAL));
        
        inviteMenuItem = new JMenuItem("Invite to Game");
        inviteMenuItem.setBackground(GREY_BLUE);
        inviteMenuItem.setForeground(OFF_WHITE);
        inviteMenuItem.setFont(FONT_MAIN_PLAIN);
        inviteMenuItem.setOpaque(true);
        
        inviteMenuItem.addActionListener(e -> {
            Player selectedPlayer = playerList.getSelectedValue();
            if (selectedPlayer != null) {
                String targetUsername = selectedPlayer.getUsername();
                showCreateRoomDialog(targetUsername);
            }
        });
        playerContextMenu.add(inviteMenuItem);
    }
    
    private void initGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(CHARCOAL_BLUE);
        
        JPanel roomInfoPanel = new JPanel(new BorderLayout());
        roomInfoPanel.setBackground(GREY_BLUE);
        roomInfoPanel.setBorder(createThemedTitledBorder("Game Room"));
        roomInfoPanel.setPreferredSize(new Dimension(400, 0));

        roomNameLabel = new JLabel("Room: ");
        roomNameLabel.setFont(FONT_MAIN_BOLD.deriveFont(22f));
        roomNameLabel.setForeground(VIBRANT_TEAL);
        roomNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        roomInfoPanel.add(roomNameLabel, BorderLayout.NORTH);
        
        roomPlayerListModel = new DefaultListModel<>();
        roomPlayerList = new JList<>(roomPlayerListModel);
        styleList(roomPlayerList);
        roomPlayerList.setCellRenderer(new RoomPlayerListRenderer());
        JScrollPane roomPlayerScrollPane = new JScrollPane(roomPlayerList);
        roomPlayerScrollPane.setBorder(null);
        roomInfoPanel.add(roomPlayerScrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(GREY_BLUE);
        
        readyButton = new JButton("Ready");
        styleButton(readyButton, CLEAR_GREEN, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(18f));
        
        startGameButton = new JButton("Start Game");
        styleButton(startGameButton, VIBRANT_TEAL, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(18f));
        
        leaveRoomButton = new JButton("Leave Room");
        styleButton(leaveRoomButton, SOFT_RED, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(18f));
        
        readyButton.addActionListener(e -> clientControl.sendCommand(new Command(Command.Type.PLAYER_READY, currentUsername, currentRoom.getRoomId())));
        startGameButton.addActionListener(e -> clientControl.sendCommand(new Command(Command.Type.START_GAME, currentUsername, currentRoom.getRoomId())));
        leaveRoomButton.addActionListener(e -> leaveCurrentRoom());
        
        buttonPanel.add(readyButton);
        buttonPanel.add(startGameButton);
        buttonPanel.add(leaveRoomButton);
        roomInfoPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        gamePanel.add(roomInfoPanel, BorderLayout.WEST);

        JPanel centerGamePanel = new JPanel(new BorderLayout());
        centerGamePanel.setBackground(CHARCOAL_BLUE);
        
        gameBoardPanel = new JPanel();
        gameBoardPanel.setBackground(CHARCOAL_BLUE);
        centerGamePanel.add(gameBoardPanel, BorderLayout.CENTER);
        
        topGamePanel = new JPanel(new GridLayout(1, 3));
        topGamePanel.setBackground(GREY_BLUE);
        
        player1ScoreLabel = new JLabel("Player 1: 0");
        player2ScoreLabel = new JLabel("Player 2: 0");
        turnStatusLabel = new JLabel("Waiting...");
        countdownLabel = new JLabel("Ready...");
        
        player1ScoreLabel.setFont(FONT_MAIN_BOLD.deriveFont(22f));
        player1ScoreLabel.setForeground(OFF_WHITE);
        player2ScoreLabel.setFont(FONT_MAIN_BOLD.deriveFont(22f));
        player2ScoreLabel.setForeground(OFF_WHITE);
        turnStatusLabel.setFont(FONT_MAIN_BOLD.deriveFont(22f));
        turnStatusLabel.setForeground(LIGHT_GREY);
        countdownLabel.setFont(FONT_MAIN_BOLD.deriveFont(22f));
        countdownLabel.setForeground(LIGHT_GREY);
        
        player1ScoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
        player2ScoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
        turnStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        topGamePanel.add(player1ScoreLabel);
        topGamePanel.add(turnStatusLabel);
        topGamePanel.add(player2ScoreLabel);
        
        centerGamePanel.add(topGamePanel, BorderLayout.NORTH);
        
        gameControlPanel = new JPanel(new FlowLayout());
        gameControlPanel.setBackground(CHARCOAL_BLUE);
        
        quitGameButton = new JButton("Quit Game");
        styleButton(quitGameButton, SOFT_RED, OFF_WHITE, FONT_MAIN_BOLD.deriveFont(18f));
        quitGameButton.addActionListener(e -> quitGame());
        gameControlPanel.add(quitGameButton);
        
        JPanel southInfoPanel = new JPanel(new BorderLayout(10, 0));
        southInfoPanel.setBackground(CHARCOAL_BLUE);

        countdownLabel.setFont(FONT_MAIN_BOLD.deriveFont(22f));
        countdownLabel.setForeground(LIGHT_GREY);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        turnTimerBar = new JProgressBar(0, TURN_DURATION_SEC);
        turnTimerBar.setValue(TURN_DURATION_SEC);
        turnTimerBar.setStringPainted(true);
        turnTimerBar.setString(TURN_DURATION_SEC + "s");
        turnTimerBar.setFont(FONT_MAIN_BOLD);
        turnTimerBar.setForeground(CLEAR_GREEN);
        turnTimerBar.setBackground(GREY_BLUE);
        turnTimerBar.setPreferredSize(new Dimension(200, 30));
        turnTimerBar.setVisible(false);

        southInfoPanel.add(countdownLabel, BorderLayout.CENTER);
        southInfoPanel.add(turnTimerBar, BorderLayout.EAST);
        
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBackground(CHARCOAL_BLUE);
        southPanel.add(southInfoPanel, BorderLayout.NORTH);
        southPanel.add(gameControlPanel, BorderLayout.CENTER);

        centerGamePanel.add(southPanel, BorderLayout.SOUTH);
        
        gamePanel.add(centerGamePanel, BorderLayout.CENTER);
    }
    
    private void showLobbyView() {
        this.setSize(1400, 1000);
        this.setLocationRelativeTo(null);
        currentRoom = null;
        currentGameState = null;
        mainLayout.show(mainPanel, "LOBBY");
        setTitle("Lobby - Welcome, " + currentUsername);
    }
    
    private void showGameRoomView(GameRoom room) {
        this.setSize(1400, 1000);
        this.setLocationRelativeTo(null);
        currentRoom = room;
        mainLayout.show(mainPanel, "GAME");
        updateRoomState(room);
    }
    
    private void stopAllTimers() {
        if (swingTurnTimer.isRunning()) {
            swingTurnTimer.stop();
        }
        if (swingPrepareTimer.isRunning()) {
            swingPrepareTimer.stop();
        }
        turnTimerBar.setVisible(false);
    }
    
    public void handleServerCommand(Command command) {
        System.out.println("[CLIENT " + currentUsername + "] Received command: " + command.getType());
        
        switch (command.getType()) {
            case LOGIN_SUCCESS:
                stopAllTimers();
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
                if (response.startsWith("Error")) {
                    JOptionPane.showMessageDialog(this, response, "Login Error", JOptionPane.ERROR_MESSAGE);
                    resetLoginControls();
                }
                break;
                
            case RECEIVE_INVITE:
                String inviter = command.getUsername();
                String roomId = (String) command.getData();
                int choice = JOptionPane.showConfirmDialog(this, 
                        inviter + " invites you to room " + roomId + ". Do you want to join?",
                        "Game Invite", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    clientControl.sendCommand(new Command(Command.Type.ACCEPT_INVITE, currentUsername, roomId));
                } else {
                    clientControl.sendCommand(new Command(Command.Type.DECLINE_INVITE, currentUsername, roomId));
                }
                break;

          case RECEIVE_INVITE_DECLINED:
                String declineMsg = (String) command.getData();
                JOptionPane.showMessageDialog(this, declineMsg, "Invite Declined", JOptionPane.INFORMATION_MESSAGE);
                break;
                
            case CREATE_ROOM_SUCCESS:
            case JOIN_ROOM_SUCCESS:
                stopAllTimers();
                GameRoom joinedRoom = (GameRoom) command.getData();
                showGameRoomView(joinedRoom);
                break;
                
            case UPDATE_ROOM_STATE:
                stopAllTimers();
                GameRoom updatedRoom = (GameRoom) command.getData();
                if (currentRoom != null && currentRoom.getRoomId().equals(updatedRoom.getRoomId())) {
                    System.out.println("Client " + currentUsername + " - Updating room state. Players received: " + updatedRoom.getPlayerCount() + ", Players: " + updatedRoom.getPlayers().stream().map(p -> p.getUsername()).collect(java.util.stream.Collectors.toList()));
                    updateRoomState(updatedRoom);
                }
                break;
                
            case GAME_STARTED:
                stopAllTimers();
                currentGameState = (GameState) command.getData();
                renderGameBoard(currentGameState);
                break;
                
            case GAME_UPDATE:
                GameState state = (GameState) command.getData();
                String msg = state.getMessage();

                if (msg.startsWith("Get Ready!")) {
                    stopAllTimers();
                } else {
                    if (swingPrepareTimer.isRunning()) {
                        swingPrepareTimer.stop();
                    }
                }
                
                currentGameState = state;
                renderGameBoard(currentGameState);
                break;
                
            case GAME_OVER:
                stopAllTimers();
                currentGameState = (GameState) command.getData();
                renderGameBoard(currentGameState);
                showGameOverDialog(currentGameState.getMessage());
                break;
                
            case OPPONENT_LEFT:
                stopAllTimers();
                currentGameState = (GameState) command.getData();
                if (currentGameState != null) {
                    renderGameBoard(currentGameState);
                    countdownLabel.setText(currentGameState.getMessage());
                    JOptionPane.showMessageDialog(this, currentGameState.getMessage(), "Game Over", JOptionPane.INFORMATION_MESSAGE);
                }
                break;
                
            case JOIN_ROOM_FAILED:
                String errorMsg = (String) command.getData();
                JOptionPane.showMessageDialog(this, errorMsg, "Join Room Failed", JOptionPane.ERROR_MESSAGE);
                break;
                
            case LEAVE_ROOM:
                stopAllTimers();
                String leaveMsg = (String) command.getData();
                JOptionPane.showMessageDialog(this, leaveMsg, "Left Room", JOptionPane.INFORMATION_MESSAGE);
                showLobbyView();
                break;
                
            case REMATCH_REQUEST:
                String rematcher = command.getUsername();
                if (currentRoom != null && currentRoom.getStatus().equals("FINISHED")) {
                    int rematchChoice = JOptionPane.showConfirmDialog(this, 
                            rematcher + " wants a rematch. Do you agree?",
                            "Rematch Request", JOptionPane.YES_NO_OPTION);
                    clientControl.sendCommand(new Command(Command.Type.REMATCH_REQUEST, currentUsername, null));
                }
                break;
                
            case SEND_MATCH_HISTORY:
                ArrayList<MatchHistoryEntry> history = (ArrayList<MatchHistoryEntry>) command.getData();
                matchHistoryDialog.updateHistory(history);
                matchHistoryDialog.setVisible(true);
                break;
                
            default:
                break;
        }
    }
    
    private void updateLeaderboard(List<Player> leaderboard) {
        leaderboardListModel.clear();
        for (Player p : leaderboard) {
            leaderboardListModel.addElement(p);
        }
    }
    
    private void showGameOverDialog(String message) {
        countdownLabel.setText(message + ".");
        int rematchChoice = JOptionPane.showConfirmDialog(this, 
                message + "\nDo you want a rematch?",
                "Game Over - Rematch?", JOptionPane.YES_NO_OPTION);

        if (rematchChoice == JOptionPane.YES_OPTION) {
            clientControl.sendCommand(new Command(Command.Type.REMATCH_REQUEST, currentUsername, null));
            countdownLabel.setText("Waiting for opponent...");
        } else {
            clientControl.sendCommand(new Command(Command.Type.REMATCH_RESPONSE, currentUsername, false));
        }
    }
    
    private void updatePlayerList(ArrayList<Player> players) {
        playerListModel.clear();
        
        List<Player> sortedPlayers = players.stream()
            .filter(p -> !p.getUsername().equalsIgnoreCase(currentUsername))
            .sorted(
                Comparator.comparing(Player::getStatus, Comparator.reverseOrder())
                .thenComparing(Player::getUsername)
            )
            .collect(Collectors.toList());

        for (Player p : sortedPlayers) {
             playerListModel.addElement(p);
        }
    }
    
    private void updateRoomList(ArrayList<GameRoom> rooms) {
        roomListModel.clear();
        for (GameRoom room : rooms) {
            String displayText = String.format("%s (%d/%d) - %d cards - %s", 
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
        setTitle("Room: " + room.getRoomId() + " - User: " + currentUsername);
        roomNameLabel.setText("Room: " + room.getRoomId() + " (" + room.getCardCount() + " cards)");
        
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
                playerText += " (Host) - Ready";
            } else {
                if (room.getReadyPlayers().stream().anyMatch(name -> name.equalsIgnoreCase(p.getUsername()))) {
                    playerText += " - Ready";
                } else {
                    playerText += " - Not Ready";
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
                readyButton.setText("Ready");
            } else {
                readyButton.setEnabled(true);
                readyButton.setText("Ready");
            }
        }
        
        startGameButton.setEnabled(room.areAllPlayersReady() && room.getPlayerCount() > 1);

        if ("WAITING".equals(room.getStatus())) {
            gameBoardPanel.removeAll();
            gameBoardPanel.revalidate();
            gameBoardPanel.repaint();
            
            gameControlPanel.setVisible(false);
            turnTimerBar.setVisible(false);
            stopAllTimers();

            String p1Name = "Player 1";
            if (room.getPlayerCount() > 0) {
                p1Name = room.getPlayers().get(0).getUsername();
            }
            player1ScoreLabel.setText(p1Name + ": 0");
            player1ScoreLabel.setForeground(OFF_WHITE);
            
            String p2Name = "Player 2";
             if (room.getPlayerCount() > 1) {
                p2Name = room.getPlayers().get(1).getUsername();
            }
            player2ScoreLabel.setText(p2Name + ": 0");
            player2ScoreLabel.setForeground(OFF_WHITE);
            
            turnStatusLabel.setText("Waiting...");
            turnStatusLabel.setForeground(LIGHT_GREY);
            
            if (room.getPlayerCount() == 1 && isHost) {
                 countdownLabel.setText("Opponent left. Waiting for new player...");
            } else if (room.getPlayerCount() == 2) {
                if (room.areAllPlayersReady()) {
                    countdownLabel.setText("Both players ready. Waiting for Host to start!");
                } else {
                    String notReadyPlayer = "Opponent";
                    Player guest = room.getPlayers().stream().filter(p -> !p.getUsername().equalsIgnoreCase(room.getHost().getUsername())).findFirst().orElse(null);
                    if (guest != null && !room.getReadyPlayers().contains(guest.getUsername())) {
                        notReadyPlayer = guest.getUsername();
                    }
                    countdownLabel.setText("Waiting for " + notReadyPlayer + " to be ready...");
                }
            } else {
                 countdownLabel.setText("Ready...");
            }
            countdownLabel.setForeground(LIGHT_GREY);

            if (currentGameState != null && "FINISHED".equals(currentGameState.getGameStatus())) {
                 currentGameState = null;
            }
            
            readyButton.setVisible(!isHost);
            startGameButton.setVisible(isHost);
        }
    }

    private void renderGameBoard(GameState state) {
        if (state == null) return;
        
        currentGameState = state;
        gameBoardPanel.removeAll();
        
        int cardCount = state.getCardValues().size();
        
        int rows = 0;
        int cols = 0;

        if (cardCount <= 16) {
            rows = 4; cols = 4;
        } else if (cardCount <= 20) {
            rows = 4; cols = 5;
        } else if (cardCount <= 24) {
            rows = 4; cols = 6;
        } else if (cardCount <= 30) {
            rows = 5; cols = 6;
        } else {
            rows = 6; cols = 6;
        }
        
        gameBoardPanel.setLayout(new GridLayout(rows, cols, 10, 10));
        
        int panelWidth = gameBoardPanel.getParent().getWidth();
        int panelHeight = gameBoardPanel.getParent().getHeight();

        if (panelWidth <= 0 || panelHeight <= 0) {
            panelWidth = (cols * 100);
            panelHeight = (rows * 100);
        }
        
        if (topGamePanel != null && gameControlPanel != null) {
            panelHeight -= (topGamePanel.getHeight() + gameControlPanel.getParent().getHeight());
        }

        int hGap = 10;
        int vGap = 10;
        
        int cellWidth = (panelWidth - (hGap * (cols + 1))) / cols;
        int cellHeight = (panelHeight - (vGap * (rows + 1))) / rows;
        
        int dynamicCardSize = Math.max(50, Math.min(cellWidth, cellHeight));

        if (cardButtons.size() != cardCount) {
            cardButtons.clear();
            for (int i = 0; i < cardCount; i++) {
                JButton cardButton = new JButton();
                final int index = i;
                cardButton.addActionListener(e -> onCardFlipped(index));
                cardButtons.add(cardButton);
            }
        }
        
        boolean myTurn = state.getCurrentPlayerUsername().equalsIgnoreCase(currentUsername);
        String msg = state.getMessage();
        
        turnStatusLabel.setText("Turn: " + state.getCurrentPlayerUsername());
        turnStatusLabel.setForeground(myTurn ? VIBRANT_TEAL : WARM_ORANGE);
        
        if (!state.getGameStatus().equals("PLAYING")) {
            stopAllTimers();
            countdownLabel.setText("Game Over: " + msg);
            countdownLabel.setForeground(LIGHT_GREY);
            turnTimerBar.setVisible(false);
            
        } else if (msg.startsWith("Get Ready!")) {
            stopAllTimers();
            countdownLabel.setText(msg);
            countdownLabel.setForeground(LIGHT_GREY);
            
            prepareTimeRemaining = PREPARE_DURATION;
            turnTimerBar.setMaximum(PREPARE_DURATION);
            turnTimerBar.setValue(prepareTimeRemaining);
            turnTimerBar.setString(prepareTimeRemaining + "s");
            turnTimerBar.setForeground(VIBRANT_TEAL);
            turnTimerBar.setVisible(true);
            swingPrepareTimer.start();
            
        } else {
            stopAllTimers(); 
            
            if (myTurn) {
                countdownLabel.setText("YOUR TURN! " + msg);
                countdownLabel.setForeground(WARM_ORANGE);
                if (msg.startsWith("GO!")) {
                    clientFlipCount = 0;
                } else if (msg.contains("flipped 1 card")) {
                    clientFlipCount = 1;
                }
            } else {
                 countdownLabel.setText("Waiting for opponent... " + msg);
                 countdownLabel.setForeground(LIGHT_GREY);
            }

            turnTimerBar.setVisible(true);
            
            turnTimeRemaining = TURN_DURATION_SEC;
            int durationSec = state.getTurnDuration() / 1000;
            if (durationSec > 0) {
                turnTimeRemaining = durationSec;
            }
            
            turnTimerBar.setMaximum(turnTimeRemaining);
            turnTimerBar.setValue(turnTimeRemaining);
            turnTimerBar.setString(turnTimeRemaining + "s");
            turnTimerBar.setForeground(CLEAR_GREEN);
            
            swingTurnTimer.start();
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
            button.setOpaque(true);
            button.setBorder(null);
            
            if (state.isCardMatched()[j]) {
                button.setVisible(false);
            } else {
                button.setVisible(true);
                if (state.isCardFlipped()[j]) {
                    String imageName = state.getCardValues().get(j);
                    ImageIcon icon = imageCache.get(imageName);
                    ImageIcon scaledIcon = scaleImage(icon, dynamicCardSize, dynamicCardSize);
                    
                    button.setText(null);
                    button.setIcon(scaledIcon);
                    button.setDisabledIcon(scaledIcon);
                    button.setEnabled(false);
                    button.setBackground(OFF_WHITE);
                    button.setBorder(BorderFactory.createLineBorder(WARM_ORANGE, 2));
                } else {
                    button.setText("?");
                    button.setFont(FONT_MAIN_BOLD.deriveFont(Math.min(48, dynamicCardSize / 2f)));
                    button.setIcon(null);
                    button.setDisabledIcon(null);
                    button.setEnabled(myTurn && state.getGameStatus().equals("PLAYING") && !msg.startsWith("Get Ready!"));
                    button.setBackground(GREY_BLUE);
                    button.setForeground(VIBRANT_TEAL);
                    button.setBorder(BorderFactory.createLineBorder(VIBRANT_TEAL, 1));
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
            
            if (clientFlipCount == 1) {
                clientFlipCount = 0;
            } else {
                clientFlipCount++;
            }
            
            FlipData flipData = new FlipData(currentRoom.getRoomId(), cardIndex);
            clientControl.sendCommand(new Command(Command.Type.FLIP_CARD, currentUsername, flipData));
        }
    }

    private void showCreateRoomDialog(String targetUsername) {
        JPanel panel = new JPanel(new FlowLayout());
        panel.setBackground(GREY_BLUE);
        
        JLabel label = new JLabel("Select Card Count:");
        label.setForeground(LIGHT_GREY);
        label.setFont(FONT_MAIN_PLAIN);
        
        String[] options = {"16 Cards (4x4)", "20 Cards (4x5)", "24 Cards (4x6)", "30 Cards (5x6)"};
        JComboBox<String> cardCountBox = new JComboBox<>(options);
        cardCountBox.setBackground(GREY_BLUE);
        cardCountBox.setForeground(OFF_WHITE);
        cardCountBox.setFont(FONT_MAIN_PLAIN);
        
        panel.add(label);
        panel.add(cardCountBox);
        
        String dialogTitle = (targetUsername == null) ? "Create New Room" : "Invite " + targetUsername + " to Room";
        
        int result = JOptionPane.showConfirmDialog(this, panel, dialogTitle, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                String selected = (String) cardCountBox.getSelectedItem();
                int cardCount = Integer.parseInt(selected.split(" ")[0]);

                if (targetUsername == null) {
                    clientControl.sendCommand(new Command(Command.Type.CREATE_ROOM, currentUsername, cardCount));
                } else {
                    InviteData inviteData = new InviteData(cardCount, targetUsername);
                    clientControl.sendCommand(new Command(Command.Type.CREATE_ROOM_AND_INVITE, currentUsername, inviteData));
                }
                
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid selection.", "Error", JOptionPane.ERROR_MESSAGE);
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
                    "Are you sure you want to quit? You will forfeit the game.",
                    "Quit Game", JOptionPane.YES_NO_OPTION);
             if (choice == JOptionPane.YES_OPTION) {
                 clientControl.sendCommand(new Command(Command.Type.QUIT_GAME, currentUsername, currentRoom.getRoomId()));
             }
        } else {
            leaveCurrentRoom();
        }
    }
    
    private void showMatchHistory() {
        clientControl.sendCommand(new Command(Command.Type.GET_MATCH_HISTORY, currentUsername, null));
    }

    public void resetLoginControls() {
        connectButton.setEnabled(true);
        usernameField.setEditable(true);
    }
    
    public void logError(String message) {
        JOptionPane.showMessageDialog(this, message, "Connection Error", JOptionPane.ERROR_MESSAGE);
        resetLoginControls();
        mainLayout.show(mainPanel, "LOGIN");
        setTitle("Game Lobby");
        
        this.setSize(600, 400); 
        this.setLocationRelativeTo(null);
    }

    private class ConnectListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            currentUsername = usernameField.getText();
            if (currentUsername.trim().isEmpty()) {
                JOptionPane.showMessageDialog(ClientView.this, "Username cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            connectButton.setEnabled(false);
            usernameField.setEditable(false);
            
            ClientView.this.setLocationRelativeTo(null);

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

    private void styleButton(JButton button, Color background, Color foreground, Font font) {
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(font);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
    }

    private void styleList(JList<?> list) {
        list.setBackground(GREY_BLUE);
        list.setForeground(OFF_WHITE);
        list.setSelectionBackground(VIBRANT_TEAL);
        list.setSelectionForeground(OFF_WHITE);
        list.setFont(FONT_MAIN_PLAIN.deriveFont(18f));
    }
    
    private TitledBorder createThemedTitledBorder(String title) {
        Border border = BorderFactory.createLineBorder(GREY_BLUE, 2);
        TitledBorder titledBorder = BorderFactory.createTitledBorder(border, title);
        titledBorder.setTitleColor(VIBRANT_TEAL);
        titledBorder.setTitleFont(FONT_MAIN_BOLD.deriveFont(16f));
        return titledBorder;
    }

    private class StatusIndicator extends JComponent {
        private boolean isOnline = false;

        public StatusIndicator() {
            setPreferredSize(new Dimension(10, 10));
        }

        public void setOnline(boolean isOnline) {
            this.isOnline = isOnline;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isOnline) {
                g.setColor(CLEAR_GREEN);
            } else {
                g.setColor(LIGHT_GREY);
            }
            g.fillOval(0, (getHeight() - 10) / 2, 10, 10);
        }
    }
    
    private class PlayerListRenderer extends JPanel implements ListCellRenderer<Player> {
        private StatusIndicator statusCircle;
        private JLabel playerNameLabel;
        private JLabel statusLabel;
        private JPanel statusPanel;

        public PlayerListRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBackground(GREY_BLUE);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            playerNameLabel = new JLabel();
            playerNameLabel.setFont(FONT_MAIN_PLAIN.deriveFont(18f));
            playerNameLabel.setOpaque(true);
            
            statusPanel = new JPanel(new BorderLayout(5, 0));
            statusPanel.setOpaque(false);

            statusCircle = new StatusIndicator();
            
            statusLabel = new JLabel();
            statusLabel.setFont(FONT_MAIN_PLAIN.deriveFont(18f));
            statusLabel.setOpaque(false);

            statusPanel.add(statusCircle, BorderLayout.WEST);
            statusPanel.add(statusLabel, BorderLayout.CENTER);

            add(playerNameLabel, BorderLayout.CENTER);
            add(statusPanel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Player> list, Player player, int index, boolean isSelected, boolean cellHasFocus) {
            
            String status = player.getStatus();
            boolean isOnline = status.equalsIgnoreCase("Online");
            
            statusCircle.setOnline(isOnline);
            
            String nameText = String.format("%s (Score: %d)",
                player.getUsername(),
                player.getTotalScore()
            );
            playerNameLabel.setText(nameText);
            statusLabel.setText(status);
            
            if (isSelected) {
                setBackground(VIBRANT_TEAL);
                playerNameLabel.setBackground(VIBRANT_TEAL);
                playerNameLabel.setForeground(OFF_WHITE);
                statusLabel.setForeground(OFF_WHITE);
            } else {
                setBackground(GREY_BLUE);
                playerNameLabel.setBackground(GREY_BLUE);
                
                if (isOnline) {
                    playerNameLabel.setForeground(OFF_WHITE);
                    statusLabel.setForeground(CLEAR_GREEN);
                } else {
                    playerNameLabel.setForeground(LIGHT_GREY);
                    statusLabel.setForeground(LIGHT_GREY);
                }
            }
            
            return this;
        }
    }

    private class RoomListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!isSelected) {
                c.setBackground(GREY_BLUE);
                String text = value.toString();
                if (text.contains("WAITING")) {
                    c.setForeground(WARM_ORANGE);
                } else if (text.contains("PLAYING")) {
                    c.setForeground(SOFT_RED);
                } else {
                    c.setForeground(OFF_WHITE);
                }
            } else {
                c.setBackground(VIBRANT_TEAL);
                c.setForeground(OFF_WHITE);
            }
            return c;
        }
    }
    
    private class RoomPlayerListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            c.setBackground(GREY_BLUE);
            
            String text = value.toString();
            
            if (text.contains("(Host)")) {
                c.setForeground(WARM_ORANGE);
            } else if (text.contains(" - Ready")) {
                c.setForeground(CLEAR_GREEN);
            } else if (text.contains(" - Not Ready")) {
                c.setForeground(LIGHT_GREY);
            } else {
                c.setForeground(OFF_WHITE);
            }

            if (isSelected) {
                c.setBackground(VIBRANT_TEAL.darker());
                c.setForeground(OFF_WHITE);
            }
            
            return c;
        }
    }
    
    private class LeaderboardListRenderer extends JPanel implements ListCellRenderer<Player> {
        private JLabel rankLabel;
        private JLabel nameLabel;
        private JLabel statsLabel;

        public LeaderboardListRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBackground(GREY_BLUE);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            rankLabel = new JLabel();
            rankLabel.setFont(FONT_MAIN_BOLD.deriveFont(18f));
            rankLabel.setForeground(WARM_ORANGE);
            rankLabel.setOpaque(false);
            rankLabel.setPreferredSize(new Dimension(40, 0)); 

            nameLabel = new JLabel();
            nameLabel.setFont(FONT_MAIN_PLAIN.deriveFont(18f));
            nameLabel.setForeground(OFF_WHITE);
            nameLabel.setOpaque(false);
            
            statsLabel = new JLabel();
            statsLabel.setFont(FONT_MAIN_PLAIN.deriveFont(16f));
            statsLabel.setForeground(LIGHT_GREY);
            statsLabel.setOpaque(false);

            add(rankLabel, BorderLayout.WEST);
            add(nameLabel, BorderLayout.CENTER);
            add(statsLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Player> list, Player player, int index, boolean isSelected, boolean cellHasFocus) {
            
            rankLabel.setText(String.format("%d.", index + 1));
            nameLabel.setText(player.getUsername());
            
            statsLabel.setText(String.format("Score: %d | W: %d | L: %d | D: %d", 
                    player.getTotalScore(), 
                    player.getTotalWins(),
                    player.getTotalLosses(),
                    player.getTotalDraws()));
            
            if (isSelected) {
                setBackground(VIBRANT_TEAL);
                rankLabel.setForeground(OFF_WHITE);
                nameLabel.setForeground(OFF_WHITE);
                statsLabel.setForeground(OFF_WHITE);
            } else {
                setBackground(GREY_BLUE);
                rankLabel.setForeground(WARM_ORANGE);
                nameLabel.setForeground(OFF_WHITE);
                statsLabel.setForeground(LIGHT_GREY);
            }
            
            return this;
        }
    }
    
    private class MatchHistoryDialog extends JDialog {
        private JList<MatchHistoryEntry> historyList;
        private DefaultListModel<MatchHistoryEntry> historyListModel;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        public MatchHistoryDialog(JFrame parent) {
            super(parent, "Match History (Last 20 Games)", true);
            setSize(600, 500);
            setLocationRelativeTo(parent);
            getContentPane().setBackground(CHARCOAL_BLUE);

            historyListModel = new DefaultListModel<>();
            historyList = new JList<>(historyListModel);
            styleList(historyList);
            historyList.setCellRenderer(new HistoryListRenderer());
            
            JScrollPane scrollPane = new JScrollPane(historyList);
            scrollPane.setBorder(null);
            
            add(scrollPane, BorderLayout.CENTER);
        }

        public void updateHistory(List<MatchHistoryEntry> history) {
            historyListModel.clear();
            if (history.isEmpty()) {
                historyListModel.addElement(null); 
            } else {
                for (MatchHistoryEntry entry : history) {
                    historyListModel.addElement(entry);
                }
            }
        }
        
        private class HistoryListRenderer extends JPanel implements ListCellRenderer<MatchHistoryEntry> {
            private JLabel resultLabel;
            private JLabel detailsLabel;
            private JLabel dateLabel;

            public HistoryListRenderer() {
                setLayout(new BorderLayout(10, 0));
                setBackground(GREY_BLUE);
                setOpaque(true);
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, CHARCOAL_BLUE),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
                ));

                resultLabel = new JLabel();
                resultLabel.setFont(FONT_MAIN_BOLD.deriveFont(18f));
                resultLabel.setOpaque(false);
                resultLabel.setPreferredSize(new Dimension(80, 0));

                detailsLabel = new JLabel();
                detailsLabel.setFont(FONT_MAIN_PLAIN.deriveFont(16f));
                detailsLabel.setForeground(OFF_WHITE);
                detailsLabel.setOpaque(false);

                dateLabel = new JLabel();
                dateLabel.setFont(FONT_MAIN_PLAIN.deriveFont(14f));
                dateLabel.setForeground(LIGHT_GREY);
                dateLabel.setOpaque(false);

                add(resultLabel, BorderLayout.WEST);
                add(detailsLabel, BorderLayout.CENTER);
                add(dateLabel, BorderLayout.EAST);
            }

            @Override
            public Component getListCellRendererComponent(JList<? extends MatchHistoryEntry> list, MatchHistoryEntry entry, int index, boolean isSelected, boolean cellHasFocus) {
                
                if (entry == null) {
                    detailsLabel.setText("No match history found.");
                    resultLabel.setText("");
                    dateLabel.setText("");
                    resultLabel.setForeground(LIGHT_GREY);
                    return this;
                }

                String result = entry.getResult();
                if ("Win".equals(result)) {
                    resultLabel.setForeground(CLEAR_GREEN);
                } else if ("Loss".equals(result)) {
                    resultLabel.setForeground(SOFT_RED);
                } else {
                    resultLabel.setForeground(WARM_ORANGE);
                }
                resultLabel.setText(result);

                detailsLabel.setText(String.format("vs. %s (You: %d - Opp: %d)",
                    entry.getOpponentName(),
                    entry.getMyScore(),
                    entry.getOpponentScore()
                ));
                
                dateLabel.setText(dateFormat.format(entry.getPlayedOn()));

                if (isSelected) {
                    setBackground(VIBRANT_TEAL);
                    detailsLabel.setForeground(OFF_WHITE);
                    dateLabel.setForeground(OFF_WHITE);
                } else {
                    setBackground(GREY_BLUE);
                    detailsLabel.setForeground(OFF_WHITE);
                    dateLabel.setForeground(LIGHT_GREY);
                }
                
                return this;
            }
        }
    }
}