package com.auction.core.model;

import java.time.LocalDateTime;

public class Art extends Item {
    private String artistName;

    public Art(String name, String description, double startingPrice, User owner, String artistName) {
        super(name, description, startingPrice, owner);
        this.artistName = artistName;
    }

    public Art(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, "Unknown");
    }

    public Art(String id, LocalDateTime createdAt, String name, String description,
               double startingPrice, User owner, String artistName) {
        super(id, createdAt, name, description, startingPrice, owner);
        this.artistName = artistName;
    }

    @Override

    public String getCategoryInfo() { return "Họa sĩ: " + artistName; }

    @Override

    public String getCategory() { return "Nghệ thuật"; }

    public String getArtistName() { return artistName; }
}
