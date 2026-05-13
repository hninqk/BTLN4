package com.auction.model;

import java.time.LocalDateTime;

public class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String name, String description, double startingPrice, User owner, int warrantyMonths) {
        super(name, description, startingPrice, owner);
        this.warrantyMonths = warrantyMonths;
    }

    public Electronics(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, 0);
    }

    /** DB reconstruction constructor */
    public Electronics(String id, LocalDateTime createdAt, String name, String description,
                       double startingPrice, User owner, int warrantyMonths) {
        super(id, createdAt, name, description, startingPrice, owner);
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getCategoryInfo() { return "Electronics"; }

    @Override
    public String getCategory() { return "Điện tử"; }

    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
}