package com.matchinggame.tcp;

import com.matchinggame.tcp.control.ServerControl;
import com.matchinggame.tcp.view.ServerView;
import javax.swing.SwingUtilities;

public class ServerRun {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ServerView view = new ServerView();
                ServerControl serverControl = new ServerControl(view);
                
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        serverControl.start();
                    }
                }).start();
            }
        });
    }
}