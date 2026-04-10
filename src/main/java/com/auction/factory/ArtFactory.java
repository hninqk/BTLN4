package com.auction.factory;

import com.auction.model.Art;
import com.auction.model.Item;
import com.auction.model.User;

public class ArtFactory extends ItemFactory {
    private String artistName;
    private int yearCreated;

    public ArtFactory(String artistName, int yearCreated) {
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    @Override
    public Item createItem(String name, String description, double price, User owner) {
        return new Art(name, description, price, owner, artistName, yearCreated);
    }
}
