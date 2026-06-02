package com.auction.ui.support.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class FxBackgroundTaskRunner implements BackgroundTaskRunner {
    @Override
    public <T> void run(String threadName, Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> fxTask = new Task<>() {
            @Override
            protected T call() throws Exception {
                return task.call();
            }
        };
        fxTask.setOnSucceeded(event -> onSuccess.accept(fxTask.getValue()));
        fxTask.setOnFailed(event -> Platform.runLater(() -> onFailure.accept(fxTask.getException())));

        Thread thread = new Thread(fxTask, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
