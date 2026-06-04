package com.auction.core.factory;

import com.auction.core.model.Electronics;
import com.auction.core.model.Item;
import com.auction.core.model.User;

public class ElectronicsFactory extends ItemFactory {
    private int warrantyMonths;

    public ElectronicsFactory(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    @Override

    public Item createItem(String name, String description, double price, User owner) {
        return new Electronics(name, description, price, owner, warrantyMonths);
    }

    @Override

    public Item createItem(String id, java.time.LocalDateTime createdAt, String name, String description, double price, User owner) {
        return new Electronics(id, createdAt, name, description, price, owner, warrantyMonths);
    }
}
