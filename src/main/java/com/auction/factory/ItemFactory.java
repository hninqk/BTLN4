package com.auction.factory;

import com.auction.model.*;

public class ItemFactory {
	public static Item createElectronics(String name, String description, double price, User owner, int warrantyMonths) {
		return new Electronics(name, description, price, owner, warrantyMonths);
	}

	public static Item createVehicle(String name, String description, double price, User owner, double mileage, int year) {
		return new Vehicle(name, description, price, owner, mileage, year);
	}

	public static Item createArt(String name, String description, double price, User owner, String artistName, int yearCreated) {
		return new Art(name, description, price, owner, artistName, yearCreated);
	}
}
