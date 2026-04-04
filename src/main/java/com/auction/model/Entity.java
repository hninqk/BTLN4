package com.auction.model;

import java.util.UUID;
import java.time.LocalDateTime;

public abstract class Entity {
    private final String id;
    protected final LocalDateTime createdAt;

    public Entity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { 
        return id; 
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
