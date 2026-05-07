package com.auction.model;

public class Vehicle extends Item {
    private double mileage;
    private int year;

    /** Full constructor */
    public Vehicle(String name, String description, double startingPrice, User owner, double mileage, int year) {
        super(name, description, startingPrice, owner);
        this.mileage = mileage;
        this.year = year;
    }

    /** Convenience constructor – no mileage/year */
    public Vehicle(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, 0.0, 0);
    }

    @Override
    public String getCategoryInfo() { return "Vehicle"; }

    @Override
    public String getCategory() { return "Xe cộ"; }

    public double getMileage() { return mileage; }
    public int getYear()       { return year; }
}