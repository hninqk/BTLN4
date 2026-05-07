package com.auction.model;

public class Art extends Item {
    private String artistName;
    private int yearCreated;

    /** Full constructor */
    public Art(String name, String description, double startingPrice, User owner, String artistName, int yearCreated) {
        super(name, description, startingPrice, owner);
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    /** Convenience constructor – no artist info */
    public Art(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, "Unknown", 0);
    }

    @Override
    public String getCategoryInfo() { return "Art"; }

    @Override
    public String getCategory() { return "Nghệ thuật"; }

    public String getArtistName() { return artistName; }
    public int getYearCreated()   { return yearCreated; }
}