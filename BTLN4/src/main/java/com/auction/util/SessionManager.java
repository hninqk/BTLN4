package com.auction.util;

import com.auction.model.User;

/**
 * Singleton session manager – holds the currently logged-in user.
 */
public class SessionManager {

    private static SessionManager instance;
    private User currentUser;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public User getCurrentUser() { return currentUser; }

    public void setCurrentUser(User user) { this.currentUser = user; }

    public boolean isLoggedIn() { return currentUser != null; }

    /** Clears the current session. */
    public void logout() { this.currentUser = null; }

    /** Alias kept for source compatibility with older call sites. */
    public void logoutUser() { logout(); }
}
