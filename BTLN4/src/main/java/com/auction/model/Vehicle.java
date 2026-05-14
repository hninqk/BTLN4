package com.auction.model;

import java.time.LocalDateTime;

public class Vehicle extends Item {
    private double mileage;
    private int year;

    public Vehicle(String name, String description, double startingPrice, User owner, double mileage, int year) {
        super(name, description, startingPrice, owner);
        this.mileage = mileage;
        this.year = year;
    }

    public Vehicle(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, 0.0, 0);
    }

    /** DB reconstruction constructor */
    public Vehicle(String id, LocalDateTime createdAt, String name, String description,
                   double startingPrice, User owner, double mileage, int year) {
        super(id, createdAt, name, description, startingPrice, owner);
        this.mileage = mileage;
        this.year = year;
    }

    @Override
    public String getCategoryInfo() { return "Vehicle"; }

    @Override
    public String getCategory() { return "Xe cộ"; }

    public double getMileage() { return mileage; }
    public int getYear()       { return year; }
}