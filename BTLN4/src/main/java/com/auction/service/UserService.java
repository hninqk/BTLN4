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

    /**
     * Seeds the DB with a fixed set of demo users.
     * UUIDs are DETERMINISTIC (derived from the username) so both the server
     * and any client that seeds its own local DB will always produce the same
     * IDs. This is critical: bidderId sent in WS bid messages must exist in
     * the server's DB.
     */
    private void ensureSeeded() {
        if (!userRepo.findAll().isEmpty()) return; // already seeded

        userRepo.save(makeAdmin   ("admin", "123"));
        userRepo.save(makeBidder  ("alice", "123", 5_000_000.0));
        userRepo.save(makeBidder  ("bob",   "123", 3_000_000.0));
        userRepo.save(makeSeller  ("carol", "123", "Carol Shop"));
        userRepo.save(makeSeller  ("dave",  "123", "Dave Auctions"));
        System.out.println("[UserService] Seed data inserted.");
    }

    // Helpers that produce deterministic IDs based on username
    private static final java.time.LocalDateTime SEED_TIME = java.time.LocalDateTime.of(2025, 1, 1, 0, 0);

    private Admin makeAdmin(String username, String password) {
        return new Admin(deterministicId("user-" + username), SEED_TIME, username, password, 1);
    }
    private Bidder makeBidder(String username, String password, double balance) {
        return new Bidder(deterministicId("user-" + username), SEED_TIME, username, password, balance);
    }
    private Seller makeSeller(String username, String password, String shopName) {
        return new Seller(deterministicId("user-" + username), SEED_TIME, username, password, shopName, 0.0, 0);
    }

    /** UUID derived from a fixed string — always the same across JVM restarts. */
    public static String deterministicId(String key) {
        return java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
