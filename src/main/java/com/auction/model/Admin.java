package com.auction.model;

public class Admin extends User {

    public int accessLevel;
    
    public Admin (String username, String password, int acessLevel) {
        super(username, password);
        this.accessLevel = acessLevel;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }
}
