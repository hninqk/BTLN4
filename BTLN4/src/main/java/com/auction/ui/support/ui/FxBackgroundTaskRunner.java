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
        fxTask.setOnSucceeded(event -> {
            if (onSuccess != null) {
                onSuccess.accept(fxTask.getValue());
            }
        });
        fxTask.setOnFailed(event -> {
            if (onFailure != null) {
                Platform.runLater(() -> onFailure.accept(fxTask.getException()));
            } else {
                // Default error logging if no handler is provided
                Throwable ex = fxTask.getException();
                if (ex != null) ex.printStackTrace();
            }
        });
        Thread thread = new Thread(fxTask, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
