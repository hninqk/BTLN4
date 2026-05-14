package com.auction;

import com.auction.service.AppFacade;
import com.auction.model.User;
import com.auction.util.ServerConfig;
import java.util.Optional;

public class TestClient {
    public static void main(String[] args) throws Exception {
        ServerConfig.setServerUrl("ws://localhost:7000/auction");
        System.out.println("Calling AppFacade register...");
        Optional<User> u = AppFacade.getInstance().register("newuser123", "123456", "Seller");
        System.out.println("Result: " + (u.isPresent() ? u.get().getUsername() : "empty"));
    }
}
