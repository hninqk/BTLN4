package com.auction.service;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UserService - handles authentication and user CRUD.
 * Singleton pattern. Currently uses in-memory mock data.
 * Replace with DB/socket integration later.
 */
public class UserService {

    private static UserService instance;
    private final List<User> users = new ArrayList<>();

    private UserService() {
        seedData();
    }

    public static UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }

    // ------- Authentication -------

    public Optional<User> login(String username, String password) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
                .findFirst();
    }

    public boolean register(String username, String password, String role) {
        boolean exists = users.stream().anyMatch(u -> u.getUsername().equals(username));
        if (exists)
            return false;

        User newUser = switch (role.toUpperCase()) {
            case "SELLER" -> new Seller(username, password, username + "_Shop");
            case "ADMIN" -> new Admin(username, password);
            default -> new Bidder(username, password, 0.0);
        };
        users.add(newUser);
        return true;
    }

    // ------- CRUD -------

    public List<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    public Optional<User> findByUsername(String username) {
        return users.stream().filter(u -> u.getUsername().equals(username)).findFirst();
    }

    public boolean deleteUser(String userId) {
        return users.removeIf(u -> u.getId().equals(userId));
    }

    // ------- Seed mock data -------

    private void seedData() {
        users.add(new Admin("admin", "123"));
        users.add(new Bidder("alice", "123", 5000.0));
        users.add(new Bidder("bob", "123", 3000.0));
        users.add(new Seller("carol", "123", "Carol Shop"));
        users.add(new Seller("dave", "123", "Dave Auctions"));
    }
}
