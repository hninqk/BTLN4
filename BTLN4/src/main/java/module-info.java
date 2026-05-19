module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires javafx.base;

    requires java.net.http;
    requires java.sql;
    requires java.desktop;
    requires io.javalin;
    requires com.google.gson;
    requires org.slf4j;
    requires com.zaxxer.hikari;

    // Open model to JavaFX property binding and Gson
    opens com.auction.model to javafx.base, javafx.fxml, com.google.gson;

    // Open all controller packages to FXML loader
    opens com.auction to javafx.fxml;
    opens com.auction.controller to javafx.fxml;
    opens com.auction.util to javafx.fxml, com.google.gson;
    opens com.auction.service to javafx.fxml;

    // Exports
    exports com.auction;
    exports com.auction.controller;
    exports com.auction.model;
    exports com.auction.service;
    exports com.auction.util;
    exports com.auction.exception;
    exports com.auction.repository;
    exports com.auction.client;
    exports com.auction.server;
}