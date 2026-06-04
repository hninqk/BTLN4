package com.auction.core.model;

import java.time.LocalDateTime;

public class Bidder extends User {

    private double accountBalance;

    private double frozenBalance;

    public Bidder(String username, String password, double accountBalance) {
        super(username, password);
        this.accountBalance = accountBalance;
        this.frozenBalance  = 0.0;
    }

    public Bidder(String id, LocalDateTime createdAt, String username, String password, double accountBalance) {
        super(id, createdAt, username, password);
        this.accountBalance = accountBalance;
        this.frozenBalance  = 0.0;
    }

    public Bidder(String id, LocalDateTime createdAt, String username, String password,
                  double accountBalance, double frozenBalance) {
        super(id, createdAt, username, password);
        this.accountBalance = accountBalance;
        this.frozenBalance  = Math.max(0.0, frozenBalance);
    }

    @Override

    public String getRole() { return "Bidder"; }

    public double getAccountBalance() { return accountBalance; }

    public double getFrozenBalance() { return frozenBalance; }

    public double getAvailableBalance() {
        return Math.max(0.0, accountBalance - frozenBalance);
    }

    public void setAccountBalance(double accountBalance) { this.accountBalance = accountBalance; }

    public void setFrozenBalance(double frozenBalance) {
        this.frozenBalance = Math.max(0.0, frozenBalance);
    }

    public void AddBalance(double amount) {
        if (amount > 0) accountBalance += amount;
    }

    public void addBalance(double amount) { AddBalance(amount); }

    public synchronized boolean freezeFunds(double amount) {
        if (amount <= 0) return false;
        if (getAvailableBalance() < amount) return false;
        this.frozenBalance += amount;
        return true;
    }

    public synchronized void unfreezeFunds(double amount) {
        if (amount <= 0) return;
        this.frozenBalance = Math.max(0.0, this.frozenBalance - amount);
    }

    public synchronized boolean deductBalance(double amount) {
        if (amount > 0 && this.accountBalance >= amount) {
            this.accountBalance -= amount;

            this.frozenBalance = Math.max(0.0, this.frozenBalance - amount);
            return true;
        }
        return false;
    }
}
