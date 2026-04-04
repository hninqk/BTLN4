package com.auction.app;

public class Launcher {
    public static void main(String[] args) {
        // This tricks Java into starting without checking for JavaFX modules first
        AdminServerGUI.main(args);
    }
}