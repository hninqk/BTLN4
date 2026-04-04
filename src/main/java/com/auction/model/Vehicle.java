package com.auction.model;

public class Vehicle extends Item {
    private double mileage;
    private int year;

    public Vehicle(String name, String description, double startingPrice, User owner, double mileage, int year) {
        super(name, description, startingPrice, owner);
        this.mileage = mileage;
        this.year = year;
    }

    @Override
    public String getCategoryInfo() {
        return "Vehicle";
    }
}