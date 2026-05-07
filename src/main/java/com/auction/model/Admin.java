package com.auction.model;

public class Admin extends User {

    public int accessLevel;

    public Admin(String username, String password, int accessLevel) {
        super(username, password);
        this.accessLevel = accessLevel;
    }

    /** Convenience constructor – default access level 1. */
    public Admin(String username, String password) {
        this(username, password, 1);
    }

    @Override
    public String getRole() { return "Admin"; }

    public int getAccessLevel() { return accessLevel; }
    public void setAccessLevel(int accessLevel) { this.accessLevel = accessLevel; }
}
