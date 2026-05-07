package com.auction.model;

public class Electronics extends Item {
    private int warrantyMonths;

    /** Full constructor */
    public Electronics(String name, String description, double startingPrice, User owner, int warrantyMonths) {
        super(name, description, startingPrice, owner);
        this.warrantyMonths = warrantyMonths;
    }

    /** Convenience constructor – 0 warranty months */
    public Electronics(String name, String description, double startingPrice, User owner) {
        this(name, description, startingPrice, owner, 0);
    }

    @Override
    public String getCategoryInfo() { return "Electronics"; }

    @Override
    public String getCategory() { return "Điện tử"; }

    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
}