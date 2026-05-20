package com.auction.controller;

import com.auction.util.NavigationManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;

public class SettingsController {

    @FXML private ToggleButton btnDarkModeToggle;

    @FXML
    public void initialize() {
        btnDarkModeToggle.setSelected(NavigationManager.getInstance().isDarkMode());
        updateButtonText();
    }

    @FXML
    private void handleToggleDarkMode(ActionEvent event) {
        boolean isDark = btnDarkModeToggle.isSelected();
        NavigationManager.getInstance().setDarkMode(isDark);
        updateButtonText();
    }

    private void updateButtonText() {
        if (btnDarkModeToggle.isSelected()) {
            btnDarkModeToggle.setText("Đã bật");
        } else {
            btnDarkModeToggle.setText("Đã tắt");
        }
    }
}
