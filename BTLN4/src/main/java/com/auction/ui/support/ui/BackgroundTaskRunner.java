package com.auction.ui.support.ui;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public interface BackgroundTaskRunner {
    <T> void run(String threadName, Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure);
}
