package com.auction.model;

public class Art extends Item {
    private String artistName;
    private int yearCreated;

    public Art(String name, String description, double startingPrice, User owner, String artistName, int yearCreated) {
        super(name, description, startingPrice, owner);
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    @Override
    public String getCategoryInfo() {
        return "Art";
    }
}