package com.auction.util;

import com.auction.model.User;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Singleton session manager – holds the currently logged-in user.
 * Supports session-change listeners so UI components (e.g. sidebar balance
 * pill) can refresh automatically whenever the user object is updated.
 */
public class SessionManager {

    private static SessionManager instance;
    private User currentUser;

    /** Listeners notified on every setCurrentUser() call (on the calling thread). */
    private final Set<Runnable> changeListeners = new CopyOnWriteArraySet<>();

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public User getCurrentUser() { return currentUser; }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        changeListeners.forEach(Runnable::run);
    }

    /** Register a callback that fires whenever the current user object changes. */
    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }

    /** Remove a previously registered callback. */
    public void removeChangeListener(Runnable listener) { changeListeners.remove(listener); }

    public boolean isLoggedIn() { return currentUser != null; }

    /** Clears the current session. */
    public void logout() { 
        this.currentUser = null; 
        com.auction.service.NotificationHub.getInstance().reset();
        com.auction.service.NotificationHub.getInstance().disconnect();
    }

    /** Alias kept for source compatibility with older call sites. */
    public void logoutUser() { logout(); }
}
