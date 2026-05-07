module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;

    requires java.net.http;

    requires java.sql;
    requires io.javalin;
    requires com.google.gson;
    requires org.slf4j;

    opens com.auction.controller to javafx.fxml;

    opens com.auction.model to com.google.gson;

    exports com.auction;
    exports com.auction.model;
    exports com.auction.repository;
    exports com.auction.manager;
    exports com.auction.network;
}