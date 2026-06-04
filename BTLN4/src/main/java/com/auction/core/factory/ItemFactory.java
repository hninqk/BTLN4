package com.auction.core.factory;

import com.auction.core.model.*;

public abstract class ItemFactory {
	public abstract Item createItem(
        String name,
        String description,
        double price,
        User owner
    );

	public abstract Item createItem(
        String id,
        java.time.LocalDateTime createdAt,
        String name,
        String description,
        double price,
        User owner
    );
}
