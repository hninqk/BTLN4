package com.auction.model;

import java.time.LocalDateTime;

public class Bidder extends User {
    private double accountBalance;

    /** Normal constructor */
    public Bidder(String username, String password, double accountBalance) {
        super(username, password);
        this.accountBalance = accountBalance;
    }

    /** DB reconstruction constructor */
    public Bidder(String id, LocalDateTime createdAt, String username, String password, double accountBalance) {
        super(id, createdAt, username, password);
        this.accountBalance = accountBalance;
    }

    @Override
    public String getRole() { return "Bidder"; }

    public double getAccountBalance() { return accountBalance; }
    public void setAccountBalance(double accountBalance) { this.accountBalance = accountBalance; }

    public void AddBalance(double amount) {
        if (amount > 0) accountBalance += amount;
    }

    // alias kept for service layer
    public void addBalance(double amount) { AddBalance(amount); }

    public boolean deductBalance(double amount) {
        if (amount > 0 && this.accountBalance >= amount) {
            this.accountBalance -= amount;
            return true;
        }
        return false;
    }
}
