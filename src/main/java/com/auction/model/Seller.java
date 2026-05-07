package com.auction.model;

public class Seller extends User {

    private String shopName;
    private double rating;
    private int cntvoted;

    public Seller(String username, String password, String shopName) {
        super(username, password);
        this.shopName = shopName;
        this.rating = 0.0;
        this.cntvoted = 0;
    }

    @Override
    public String getRole() { return "Seller"; }

    public String getShopName()            { return shopName; }
    public void setShopName(String name)   { this.shopName = name; }
    public double getRating()              { return rating; }
    public int getCntvoted()               { return cntvoted; }

    public void updateRating(double newRating) {
        this.cntvoted++;
        this.rating = (this.rating * (cntvoted - 1) + newRating) / cntvoted;
    }
}
