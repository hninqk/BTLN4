package com.auction.model;

import java.time.LocalDateTime;

public class Admin extends User {

    public Admin(String username, String password) {
        super(username, password);
    }

    /** DB reconstruction constructor */
    public Admin(String id, LocalDateTime createdAt, String username, String password) {
        super(id, createdAt, username, password);
    }

    @Override
    public String getRole() { return "Admin"; }
}
