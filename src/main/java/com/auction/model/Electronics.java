package com.auction.model;

public class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String name, String description, double startingPrice, User owner, int warrantyMonths) {
        super(name, description, startingPrice, owner);
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getCategoryInfo() {
        return "Electronics";
    }
}