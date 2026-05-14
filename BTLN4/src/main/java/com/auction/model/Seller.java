package com.auction.model;

import java.time.LocalDateTime;

public class Seller extends User {

    private String shopName;
    private double rating;
    private int cntvoted;

    /** Normal constructor */
    public Seller(String username, String password, String shopName) {
        super(username, password);
        this.shopName = shopName;
        this.rating = 0.0;
        this.cntvoted = 0;
    }

    /** DB reconstruction constructor */
    public Seller(String id, LocalDateTime createdAt, String username, String password,
                  String shopName, double rating, int cntvoted) {
        super(id, createdAt, username, password);
        this.shopName = shopName;
        this.rating = rating;
        this.cntvoted = cntvoted;
    }

    @Override
    public String getRole() { return "Seller"; }

    public String getShopName()          { return shopName; }
    public void setShopName(String name) { this.shopName = name; }
    public double getRating()            { return rating; }
    public int getCntvoted()             { return cntvoted; }

    public void updateRating(double newRating) {
        this.cntvoted++;
        this.rating = (this.rating * (cntvoted - 1) + newRating) / cntvoted;
    }
}
