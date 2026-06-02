package com.auction.infra.factory;

import com.auction.core.model.*;

public abstract class ItemFactory {
	public abstract Item createItem(
        String name,
        String description,
        double price,
        User owner
    );
}