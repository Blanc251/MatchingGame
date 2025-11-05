/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.matchinggame.tcp.view;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class ServerView extends JFrame {

    private JTextArea logArea;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    public ServerView() {
        super("Server Management Dashboard");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null); // Đặt giữa màn hình

        // 1. Khu vực Log
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(new TitledBorder("Server Log"));

        // 2. Khu vực danh sách User (Theo yêu cầu sảnh chờ)
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("Online Users"));
        userScrollPane.setPreferredSize(new Dimension(200, 0)); // Tăng độ rộng

        // 3. Bố cục
        add(logScrollPane, BorderLayout.CENTER);
        add(userScrollPane, BorderLayout.EAST);

        setVisible(true);
    }

    // Phương thức để Controller gọi để ghi log (an toàn luồng)
    public void logMessage(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength()); // Tự cuộn xuống
            }
        });
    }

    // Phương thức để Controller thêm user (an toàn luồng)
    public void addUserToList(String username) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (!userListModel.contains(username)) {
                    userListModel.addElement(username);
                }
            }
        });
    }

    // Phương thức để Controller xóa user (an toàn luồng)
    public void removeUserFromList(String username) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                userListModel.removeElement(username);
            }
        });
    }
}