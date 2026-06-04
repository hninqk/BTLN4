package com.auction.ui.controller;

import com.auction.service.AppFacade;
import com.auction.ui.util.NavigationManager;
import com.auction.ui.support.ui.BackgroundTaskRunner;
import com.auction.ui.support.ui.FxBackgroundTaskRunner;
import com.auction.ui.util.AlertHelper;
import javafx.application.Platform;

public abstract class BaseController {

    protected final AppFacade app = AppFacade.getInstance();

    protected final NavigationManager nav = NavigationManager.getInstance();

    protected final BackgroundTaskRunner taskRunner = new FxBackgroundTaskRunner();

    protected void showError(String message) {
        Platform.runLater(() -> AlertHelper.showError("Lỗi", message));
    }

    protected void showInfo(String message) {
        Platform.runLater(() -> AlertHelper.showInfo("Thông báo", message));
    }

    public void cleanup() {

    }
}
