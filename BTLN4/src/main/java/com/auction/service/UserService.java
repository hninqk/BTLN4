package com.auction.service;

import com.auction.core.model.*;

import com.auction.infra.repository.JdbcUserRepository;
import com.auction.service.security.PasswordHashService;
import java.util.List;
import java.util.Optional;

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

    public Optional<User> login(String username, String password) {
        Optional<User> user = userRepo.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        User found = user.get();
        String storedPassword = found.getPassword();
        if (PasswordHashService.verify(storedPassword, password)) {
            return user;
        }

        return Optional.empty();
    }

    public boolean register(String username, String password, String role) {
        if (userRepo.existsByUsername(username)) return false;
        String passwordHash = PasswordHashService.hash(password);

        User newUser = switch (role.toUpperCase()) {
            case "SELLER" -> new Seller(username, passwordHash, username + "_Shop");
            case "ADMIN"  -> new Admin(username, passwordHash);
            default       -> new Bidder(username, passwordHash, 0.0);
        };
        userRepo.save(newUser);
        return true;
    }

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

    public void saveUser(User user) {
        userRepo.update(user);
    }

    private void ensureSeeded() {
        if (!userRepo.findAll().isEmpty()) return;

        userRepo.save(makeAdmin   ("admin", "123"));
        userRepo.save(makeBidder  ("alice", "123", 5_000_000.0));
        userRepo.save(makeBidder  ("bob",   "123", 3_000_000.0));
        userRepo.save(makeSeller  ("carol", "123", "Carol Shop"));
        userRepo.save(makeSeller  ("dave",  "123", "Dave Auctions"));
        System.out.println("[UserService] Seed data inserted.");
    }

    private static final java.time.LocalDateTime SEED_TIME = java.time.LocalDateTime.of(2025, 1, 1, 0, 0);

    private Admin makeAdmin(String username, String password) {
        return new Admin(deterministicId("user-" + username), SEED_TIME, username,
                PasswordHashService.hash(password));
    }

    private Bidder makeBidder(String username, String password, double balance) {
        return new Bidder(deterministicId("user-" + username), SEED_TIME, username,
                PasswordHashService.hash(password), balance);
    }

    private Seller makeSeller(String username, String password, String shopName) {
        return new Seller(deterministicId("user-" + username), SEED_TIME, username,
                PasswordHashService.hash(password), shopName);
    }

    public static String deterministicId(String key) {
        return java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
