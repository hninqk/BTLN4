package com.auction.model;

public abstract class Item extends Entity {
    private String name;
    private String description;
    private double startingPrice;
    private User owner;

    public Item(String name, String description, double startingPrice, User owner) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.owner = owner;
    }

    public abstract String getCategoryInfo();
}