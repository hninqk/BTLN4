package com.auction.model;

import java.time.LocalDateTime;

public abstract class User extends Entity {
    private String username;
    private String password;

    public User(String username, String password) {
        super();
        this.username = username;
        this.password = password;
    }

    public User(String id, LocalDateTime createdAt, String username, String password) {
        super(id, createdAt);
        this.username = username;
        this.password = password;
    }

    // Getters
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    // Setters
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }

    /** Return the role label for display purposes. */
    public abstract String getRole();
}
