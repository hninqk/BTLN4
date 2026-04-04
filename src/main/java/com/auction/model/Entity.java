package com.auction.model;

public abstract class Entity {
    private String id;
    protected final LocalDateTime createdAt;

    public Entity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { 
        return id; 
    }
}
