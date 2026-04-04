package com.auction.model;

public class Admin extends User {

    public int accessLevel;
    
    public Admin (String username, String email, String password, int acessLevel) {
        super(username, email, password);
        this.accessLevel = acessLevel;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }
}
