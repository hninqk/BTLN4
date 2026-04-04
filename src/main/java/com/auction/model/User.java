package com.auction.model;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;
    public User(String id, String username, String email, String password) {
        super(id); 
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
