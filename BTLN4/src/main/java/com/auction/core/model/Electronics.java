package com.auction.core.model;

import java.time.LocalDateTime;

public class Electronics extends Item {

    public Electronics(String name, String description, double startingPrice, User owner) {
        super(name, description, startingPrice, owner);
    }

    /** DB reconstruction constructor */
    public Electronics(String id, LocalDateTime createdAt, String name, String description,
                       double startingPrice, User owner) {
        super(id, createdAt, name, description, startingPrice, owner);
    }

    @Override
    public String getCategoryInfo() { return "Electronics"; }

    @Override
    public String getCategory() { return "Điện tử"; }
}