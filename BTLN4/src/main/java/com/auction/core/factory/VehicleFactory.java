package com.auction.core.factory;

import com.auction.core.model.Item;
import com.auction.core.model.User;
import com.auction.core.model.Vehicle;

public class VehicleFactory extends ItemFactory {
    private String brand;

    public VehicleFactory(String brand) {
        this.brand = brand;
    }

    @Override

    public Item createItem(String name, String description, double price, User owner) {
        return new Vehicle(name, description, price, owner, brand);
    }

    @Override

    public Item createItem(String id, java.time.LocalDateTime createdAt, String name, String description, double price, User owner) {
        return new Vehicle(id, createdAt, name, description, price, owner, brand);
    }
}
