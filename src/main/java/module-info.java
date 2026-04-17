module com.auction {
    requires javafx.controls;
    requires javafx.fxml;

    requires transitive javafx.graphics;

    opens com.auction.controller to javafx.fxml;

    exports com.auction;

    exports com.auction.controller;
}