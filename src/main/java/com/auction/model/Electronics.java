package com.auction.model;

public class Electronics extends Item {
    private int warrantyMonths;

    @Override
    public String getCategoryInfo() {
        return "Electronics";
    }
}