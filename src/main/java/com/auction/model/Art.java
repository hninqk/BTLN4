package com.auction.model;

public class Art extends Item {
    private String artistName;
    private int yearCreated;

    @Override
    public String getCategoryInfo() {
        return "Art";
    }
}