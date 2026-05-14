package com.auction.factory;

import com.auction.model.Item;
import com.auction.model.User;
import com.auction.model.Vehicle;

public class VehicleFactory extends ItemFactory {
    private double mileage;
    private int year;

    public VehicleFactory(double mileage, int year) {
        this.mileage = mileage;
        this.year = year;
    }

    @Override
    public Item createItem(String name, String description, double price, User owner) {
        return new Vehicle(name, description, price, owner, mileage, year);
    }
}
