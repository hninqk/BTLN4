package com.auction.core.model;

import java.time.LocalDateTime;

public class Vehicle extends Item {
    private String brand;

    public Vehicle(String name, String description, double startingPrice, User owner, String brand) {
        super(name, description, startingPrice, owner);
        this.brand = brand;
    }

    public Vehicle(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, "Unknown Brand");
    }

    public Vehicle(String id, LocalDateTime createdAt, String name, String description,
                   double startingPrice, User owner, String brand) {
        super(id, createdAt, name, description, startingPrice, owner);
        this.brand = brand;
    }

    public String getBrand() { return brand; }

    @Override
    public String getCategoryInfo() { return "Hãng: " + brand; }

    @Override
    public String getCategory() { return "Xe cộ"; }
}