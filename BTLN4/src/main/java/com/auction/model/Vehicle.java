package com.auction.model;

import java.time.LocalDateTime;

public class Vehicle extends Item {

    public Vehicle(String name, String description, double startingPrice, User owner) {
        super(name, description, startingPrice, owner);
    }

    /** DB reconstruction constructor */
    public Vehicle(String id, LocalDateTime createdAt, String name, String description,
                   double startingPrice, User owner) {
        super(id, createdAt, name, description, startingPrice, owner);
    }

    @Override
    public String getCategoryInfo() { return "Vehicle"; }

    @Override
    public String getCategory() { return "Xe cộ"; }
}