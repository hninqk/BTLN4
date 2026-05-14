package com.auction;

import com.auction.model.User;
import com.auction.service.UserService;
import com.auction.util.DatabaseConnection;
import java.util.Optional;

public class TestRegister {
    public static void main(String[] args) throws Exception {
        DatabaseConnection.initialize();
        UserService userService = UserService.getInstance();
        boolean ok = userService.register("john", "123456", "Seller");
        System.out.println("Register OK: " + ok);
        Optional<User> u = userService.findByUsername("john");
        System.out.println("User present: " + u.isPresent());
        if (u.isPresent()) {
            System.out.println("Role: " + u.get().getRole());
            System.out.println("Class: " + u.get().getClass().getName());
        }
    }
}
