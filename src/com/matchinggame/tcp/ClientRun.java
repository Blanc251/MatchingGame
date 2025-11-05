package com.matchinggame.tcp;

import com.matchinggame.tcp.view.ClientView;
import javax.swing.SwingUtilities;

public class ClientRun {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ClientView();
            }   
        });
    }
}