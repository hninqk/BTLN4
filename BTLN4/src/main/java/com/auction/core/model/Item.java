package com.auction.core.model;

import java.time.LocalDateTime;

public abstract class Item extends Entity {
    private String name;

    private String description;

    private double startingPrice;

    private User owner;

    private String imageUrl;

    public Item(String name, String description, double startingPrice, User owner) {
        super();
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.owner = owner;
        this.imageUrl = "";
    }

    public Item(String id, LocalDateTime createdAt, String name, String description,
            double startingPrice, User owner) {
        super(id, createdAt);
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.owner = owner;
        this.imageUrl = "";
    }

    public abstract String getCategoryInfo();

    public String getCategory() {
        return getCategoryInfo();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public User getOwner() {
        return owner;
    }

    public String getImageUrl() {
        return imageUrl != null ? imageUrl : "";
    }

    public double getBasePrice() {
        return startingPrice;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartingPrice(double price) {
        this.startingPrice = price;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
