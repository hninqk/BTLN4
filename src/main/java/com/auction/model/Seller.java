package com.auction.model;

public class Seller extends User {

    private String shopName;
    private double rating;
    private int cntvoted;

    public Seller (String username, String password, String shopName) {
        super(username, password);
        this.shopName = shopName;
        this.rating = 0.0;
        this.cntvoted = 0;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public double getRating() {
        return rating;
    }

    public void updateRating(double newRating) {
        this.cntvoted++;
        this.rating = (this.rating + newRating) / cntvoted;
    }
}
