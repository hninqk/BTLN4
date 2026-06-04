package com.auction.core.model;

import java.time.LocalDateTime;

public class Seller extends User {

    private String shopName;

    public Seller(String username, String password, String shopName) {
        super(username, password);
        this.shopName = shopName;
    }

    public Seller(String id, LocalDateTime createdAt, String username, String password,
                  String shopName) {
        super(id, createdAt, username, password);
        this.shopName = shopName;
    }

    @Override

    public String getRole() { return "Seller"; }

    public String getShopName()          { return shopName; }

    public void setShopName(String name) { this.shopName = name; }
}
