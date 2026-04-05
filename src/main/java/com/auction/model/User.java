package com.auction.model;

import java.time.LocalDateTime;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;

    public User(String username, String email, String password) {
        super(); 
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public User(String id, LocalDateTime createdAt, String username, String email, String password) {
        super(id, createdAt); 
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
