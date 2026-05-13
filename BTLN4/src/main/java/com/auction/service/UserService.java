package com.auction.service;

import com.auction.model.*;
import com.auction.repository.JdbcUserRepository;

import java.util.List;
import java.util.Optional;

/**
 * UserService - handles authentication and user CRUD.
 * Backed by SQLite via JdbcUserRepository.
 * Falls back to seed data if DB is empty.
 */
public class UserService {

    private static UserService instance;
    private final JdbcUserRepository userRepo = new JdbcUserRepository();

    private UserService() {
        ensureSeeded();
    }

    public static UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }

    // ------- Authentication -------

    public Optional<User> login(String username, String password) {
        return userRepo.findByUsername(username)
                .filter(u -> u.getPassword().equals(password));
    }

    public boolean register(String username, String password, String role) {
        if (userRepo.existsByUsername(username)) return false;

        User newUser = switch (role.toUpperCase()) {
            case "SELLER" -> new Seller(username, password, username + "_Shop");
            case "ADMIN"  -> new Admin(username, password);
            default       -> new Bidder(username, password, 0.0);
        };
        userRepo.save(newUser);
        return true;
    }

    // ------- CRUD -------

    public List<User> getAllUsers() {
        return userRepo.findAll();
    }

    public Optional<User> findByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    public Optional<User> findById(String id) {
        return userRepo.findById(id);
    }

    public boolean deleteUser(String userId) {
        return userRepo.deleteById(userId);
    }

    /** Persist any in-memory changes (balance, shopName, password, etc.) back to DB. */
    public void saveUser(User user) {
        userRepo.update(user);
    }

    // ------- Seed -------

    private void ensureSeeded() {
        if (!userRepo.findAll().isEmpty()) return; // already seeded

        userRepo.save(new Admin("admin", "123"));
        userRepo.save(new Bidder("alice", "123", 5000.0));
        userRepo.save(new Bidder("bob", "123", 3000.0));
        userRepo.save(new Seller("carol", "123", "Carol Shop"));
        userRepo.save(new Seller("dave", "123", "Dave Auctions"));
        System.out.println("[UserService] Seed data inserted.");
    }
}
