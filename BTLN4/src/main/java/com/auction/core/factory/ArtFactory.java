package com.auction.core.factory;

import com.auction.core.model.Art;
import com.auction.core.model.Item;
import com.auction.core.model.User;

public class ArtFactory extends ItemFactory {
    private String artistName;

    public ArtFactory(String artistName) {
        this.artistName = artistName;
    }

    @Override
    public Item createItem(String name, String description, double price, User owner) {
        return new Art(name, description, price, owner, artistName);
    }

    @Override
    public Item createItem(String id, java.time.LocalDateTime createdAt, String name, String description, double price, User owner) {
        return new Art(id, createdAt, name, description, price, owner, artistName);
    }
}
