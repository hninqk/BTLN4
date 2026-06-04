package com.auction.core.model;

import java.time.LocalDateTime;

public class Electronics extends Item {

    private int warrantyMonths;

    public Electronics(String name, String description, double startingPrice, User owner, int warrantyMonths) {
        super(name, description, startingPrice, owner);
        this.warrantyMonths = warrantyMonths;
    }

    public Electronics(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, 12);
    }

    public Electronics(String id, LocalDateTime createdAt, String name, String description,
                       double startingPrice, User owner, int warrantyMonths) {
        super(id, createdAt, name, description, startingPrice, owner);
        this.warrantyMonths = warrantyMonths;
    }

    public int getWarrantyMonths() { return warrantyMonths; }

    @Override

    public String getCategoryInfo() { return "Bảo hành: " + warrantyMonths + " tháng"; }

    @Override

    public String getCategory() { return "Điện tử"; }
}
