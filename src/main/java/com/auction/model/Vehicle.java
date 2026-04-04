package com.auction.model;

public class Vehicle extends Item {
    private double mileage;
    private int year;

    @Override
    public String getCategoryInfo() {
        return "Vehicle";
    }
}