package com.auction.core.factory;

import com.auction.core.model.Art;
import com.auction.core.model.Item;
import com.auction.core.model.User;

public class ArtFactory extends ItemFactory {
    private String artistName;
    private int yearCreated;

    public ArtFactory(String artistName, int yearCreated) {
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    @Override
    public Item createItem(String name, String description, double price, User owner) {
        return new Art(name, description, price, owner, artistName);
    }
}
