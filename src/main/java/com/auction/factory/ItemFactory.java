package com.auction.factory;

import com.auction.model.*;

public abstract class ItemFactory {
	public abstract Item createItem(
        String name,
        String description,
        double price,
        User owner
    );
}