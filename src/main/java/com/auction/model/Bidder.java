package com.auction.model;

public class Bidder extends User {
    private double accountBalance;

    public Bidder(String username, String password, double accountBalance) {
        super(username, password);
        this.accountBalance = accountBalance;
    }

    @Override
    public String getRole() { return "Bidder"; }

    public double getAccountBalance()                      { return accountBalance; }
    public void setAccountBalance(double accountBalance)   { this.accountBalance = accountBalance; }

    public void addBalance(double amount) {
        if (amount > 0) accountBalance += amount;
    }

    public boolean deductBalance(double amount) {
        if (amount > 0 && this.accountBalance >= amount) {
            this.accountBalance -= amount;
            return true;
        }
        return false;
    }
}
