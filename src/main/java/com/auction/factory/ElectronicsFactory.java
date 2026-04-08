package com.auction.factory;

import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.User;

public class ElectronicsFactory extends ItemFactory {
    private int warrantyMonths;

    public ElectronicsFactory(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public Item createItem(String name, String description, double price, User owner) {
        return new Electronics(name, description, price, owner, warrantyMonths);
    }
}
