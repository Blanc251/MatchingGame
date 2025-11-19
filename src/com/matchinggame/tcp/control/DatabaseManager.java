package com.matchinggame.tcp.control;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.matchinggame.tcp.model.MatchHistoryEntry;
import com.matchinggame.tcp.model.Player;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/matching_game?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh"; 
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Make sure you added the JAR to the classpath.");
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public List<Player> loadAllPlayers() {
        List<Player> allPlayers = new ArrayList<>();
        String sql = "SELECT username, total_score, total_wins, total_losses, total_draws FROM player";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Player player = new Player(
                    rs.getString("username"), 
                    rs.getInt("total_score"), 
                    "Offline",
                    rs.getInt("total_wins"),
                    rs.getInt("total_losses"),
                    rs.getInt("total_draws")
                );
                allPlayers.add(player);
            }
        } catch (SQLException e) {
            System.err.println("Error loading all players from database. Please check XAMPP and database setup.");
        }
        return allPlayers;
    }

    public Player getPlayerByUsername(String username) {
        String sql = "SELECT username, total_score, total_wins, total_losses, total_draws FROM player WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Player(
                        rs.getString("username"), 
                        rs.getInt("total_score"), 
                        "Offline",
                        rs.getInt("total_wins"),
                        rs.getInt("total_losses"),
                        rs.getInt("total_draws")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding player: " + username);
        }
        return null;
    }

    public void insertPlayer(Player player) {
        String sql = "INSERT INTO player (username, total_score, total_wins, total_losses, total_draws) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getUsername());
            stmt.setInt(2, player.getTotalScore());
            stmt.setInt(3, player.getTotalWins());
            stmt.setInt(4, player.getTotalLosses());
            stmt.setInt(5, player.getTotalDraws());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting player: " + player.getUsername());
        }
    }
    
    public void updatePlayerStats(Player player) {
        String sql = "UPDATE player SET total_score = ?, total_wins = ?, total_losses = ?, total_draws = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, player.getTotalScore());
            stmt.setInt(2, player.getTotalWins());
            stmt.setInt(3, player.getTotalLosses());
            stmt.setInt(4, player.getTotalDraws());
            stmt.setString(5, player.getUsername());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating player: " + player.getUsername());
        }
    }
    
    public void insertMatchHistory(String p1, String p2, int p1Score, int p2Score, String winner, boolean isDraw) {
        String sql = "INSERT INTO match_history (player_one, player_two, player_one_score, player_two_score, winner_username, is_draw) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, p1);
            stmt.setString(2, p2);
            stmt.setInt(3, p1Score);
            stmt.setInt(4, p2Score);
            stmt.setString(5, winner);
            stmt.setBoolean(6, isDraw);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting match history: " + e.getMessage());
        }
    }
    
 public List<MatchHistoryEntry> getMatchHistoryForPlayer(String username) {
        List<MatchHistoryEntry> history = new ArrayList<>();
        String sql = "SELECT * FROM match_history WHERE player_one = ? OR player_two = ? ORDER BY start_time DESC LIMIT 20";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String p1 = rs.getString("player_one");
                    String p2 = rs.getString("player_two");
                    int p1Score = rs.getInt("player_one_score");
                    int p2Score = rs.getInt("player_two_score");
                    String winner = rs.getString("winner_username");
                    boolean isDraw = rs.getBoolean("is_draw");
                    
                    String opponentName;
                    int myScore;
                    int opponentScore;
                    String result;

                    if (p1.equalsIgnoreCase(username)) {
                        opponentName = p2;
                        myScore = p1Score;
                        opponentScore = p2Score;
                    } else {
                        opponentName = p1;
                        myScore = p2Score;
                        opponentScore = p1Score;
                    }

                    if (isDraw) {
                        result = "Draw";
                    } else if (winner != null && winner.equalsIgnoreCase(username)) {
                        result = "Win";
                    } else {
                        result = "Loss";
                    }
                    
                    history.add(new MatchHistoryEntry(opponentName, myScore, opponentScore, result, rs.getTimestamp("start_time")));
                }
            }
        } catch (SQLException e) {
            System.err.println("--- DATABASE ERROR (getMatchHistoryForPlayer) cho user: " + username + " ---");
            e.printStackTrace();
        }
        return history;
    }
}