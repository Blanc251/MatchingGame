package com.matchinggame.tcp.control;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.matchinggame.tcp.model.Player;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/matching_game?useSSL=false&serverTimezone=UTC"; 
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
        String sql = "SELECT username, total_score, total_wins FROM player";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Player player = new Player(
                    rs.getString("username"), 
                    rs.getInt("total_score"), 
                    "Offline",
                    rs.getInt("total_wins")
                );
                allPlayers.add(player);
            }
        } catch (SQLException e) {
            System.err.println("Error loading all players from database. Please check XAMPP and database setup.");
        }
        return allPlayers;
    }

    public Player getPlayerByUsername(String username) {
        String sql = "SELECT username, total_score, total_wins FROM player WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Player(
                        rs.getString("username"), 
                        rs.getInt("total_score"), 
                        "Offline",
                        rs.getInt("total_wins")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding player: " + username);
        }
        return null;
    }

    public void insertPlayer(Player player) {
        String sql = "INSERT INTO player (username, total_score, total_wins) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getUsername());
            stmt.setInt(2, player.getTotalScore());
            stmt.setInt(3, player.getTotalWins());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting player: " + player.getUsername());
        }
    }
    
    public void updatePlayerScoreAndWins(Player player) {
        String sql = "UPDATE player SET total_score = ?, total_wins = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, player.getTotalScore());
            stmt.setInt(2, player.getTotalWins());
            stmt.setString(3, player.getUsername());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating player: " + player.getUsername());
        }
    }
}