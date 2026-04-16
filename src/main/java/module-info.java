module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires com.google.gson;
    requires java.sql;
    requires jdk.httpserver;
    requires java.net.http;

    opens com.auction to javafx.fxml;
    opens com.auction.controller to javafx.fxml;

    exports com.auction;
}