package com.auction.core.factory;

import com.auction.core.model.Item;
import com.auction.core.model.User;
import com.auction.core.model.Vehicle;

public class VehicleFactory extends ItemFactory {
    private double mileage;
    private int year;

    public VehicleFactory(double mileage, int year) {
        this.mileage = mileage;
        this.year = year;
    }

    @Override
    public Item createItem(String name, String description, double price, User owner) {
        return new Vehicle(name, description, price, owner);
    }
}
