package com.auction.model;

import java.time.LocalDateTime;

public class Art extends Item {
    private String artistName;
    private int yearCreated;

    public Art(String name, String description, double startingPrice, User owner, String artistName, int yearCreated) {
        super(name, description, startingPrice, owner);
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    public Art(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, "Unknown", 0);
    }

    /** DB reconstruction constructor */
    public Art(String id, LocalDateTime createdAt, String name, String description,
               double startingPrice, User owner, String artistName, int yearCreated) {
        super(id, createdAt, name, description, startingPrice, owner);
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    @Override
    public String getCategoryInfo() { return "Art"; }

    @Override
    public String getCategory() { return "Nghệ thuật"; }

    public String getArtistName() { return artistName; }
    public int getYearCreated()   { return yearCreated; }
}