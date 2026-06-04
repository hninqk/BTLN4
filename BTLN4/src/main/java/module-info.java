module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires javafx.base;

    requires java.net.http;
    requires java.sql;
    requires java.desktop;
    requires java.prefs;
    requires io.javalin;
    requires com.google.gson;
    requires org.slf4j;
    requires com.zaxxer.hikari;
    requires org.bouncycastle.provider;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    opens com.auction.core.model to javafx.base, javafx.fxml, com.google.gson;

    opens com.auction to javafx.fxml;
    opens com.auction.ui.controller to javafx.fxml;
    opens com.auction.ui.support.ui to javafx.fxml;
    opens com.auction.ui.support.logic to javafx.fxml;
    opens com.auction.ui.util to javafx.fxml, com.google.gson;
    opens com.auction.core.util to javafx.fxml, com.google.gson;
    opens com.auction.api.config to javafx.fxml, com.google.gson;
    opens com.auction.infra.db to javafx.fxml, com.google.gson;
    opens com.auction.service to javafx.fxml;

    exports com.auction;
    exports com.auction.ui.controller;
    exports com.auction.ui.support.dto;
    exports com.auction.ui.support.realtime;
    exports com.auction.ui.support.ui;
    exports com.auction.ui.support.logic;
    exports com.auction.core.model;
    exports com.auction.service;
    exports com.auction.ui.util;
    exports com.auction.core.util;
    exports com.auction.api.config;
    exports com.auction.infra.db;
    exports com.auction.core.exception;
    exports com.auction.infra.repository;
    exports com.auction.api.http;
    exports com.auction.api.server;
    exports com.auction.core.factory;
    exports com.auction.service.security;
}
