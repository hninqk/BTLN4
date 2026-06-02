package com.auction.core.util;
import com.auction.ui.util.*;
import com.auction.core.util.*;
import com.auction.api.config.*;
import com.auction.infra.db.*;

import com.auction.core.model.User;
import com.auction.service.AuctionWebSocketService;
import com.auction.service.AuctionWebSocketService.AuctionWebSocketListener;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Singleton session manager – holds the currently logged-in user.
 * Supports session-change listeners so UI components (e.g. sidebar balance
 * pill) can refresh automatically whenever the user object is updated.
 */
public class SessionManager {

    private static SessionManager instance;
    private User currentUser;
    private AuctionWebSocketService globalWs;
    private final List<AuctionWebSocketListener> wsListeners = new CopyOnWriteArrayList<>();

    /** Listeners notified on every setCurrentUser() call (on the calling thread). */    private final Set<Runnable> changeListeners = new CopyOnWriteArraySet<>();

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
        
        if (user != null) {
            startGlobalWs();
        } else {
            stopGlobalWs();
        }
    }

    private void startGlobalWs() {
        if (globalWs != null && globalWs.isConnected()) return;
        
        globalWs = new AuctionWebSocketService(null, new AuctionWebSocketListener() {
            @Override public void onWsConnected() { wsListeners.forEach(l -> l.onWsConnected()); }
            @Override public void onWsDisconnected(String err) { wsListeners.forEach(l -> l.onWsDisconnected(err)); }
            @Override public void onWsError(String err) { wsListeners.forEach(l -> l.onWsError(err)); }
            @Override public void onBidUpdate(JsonObject j) { wsListeners.forEach(l -> l.onBidUpdate(j)); }
            @Override public void onAutoBidLog(JsonObject j) { wsListeners.forEach(l -> l.onAutoBidLog(j)); }
            @Override public void onAutoBidAck(JsonObject j) { wsListeners.forEach(l -> l.onAutoBidAck(j)); }
            @Override public void onAutoBidStatus(JsonObject j) { wsListeners.forEach(l -> l.onAutoBidStatus(j)); }
            @Override public void onAutoBidDeactivated(JsonObject j) { wsListeners.forEach(l -> l.onAutoBidDeactivated(j)); }
            @Override public void onStatusChanged(JsonObject j) { wsListeners.forEach(l -> l.onStatusChanged(j)); }
            @Override public void onBalanceUpdate(JsonObject j) { wsListeners.forEach(l -> l.onBalanceUpdate(j)); }
            @Override public void onFullSync(JsonObject j) { wsListeners.forEach(l -> l.onFullSync(j)); }
            @Override public void onOutbid(JsonObject j) { wsListeners.forEach(l -> l.onOutbid(j)); }
            @Override public void onLegacyBidUpdate(JsonObject j) { wsListeners.forEach(l -> l.onLegacyBidUpdate(j)); }
        });
        globalWs.connect();
    }

    private void stopGlobalWs() {
        if (globalWs != null) {
            globalWs.disconnect();
            globalWs = null;
        }
    }

    public void addWsListener(AuctionWebSocketListener l) { wsListeners.add(l); }
    public void removeWsListener(AuctionWebSocketListener l) { wsListeners.remove(l); }
    public AuctionWebSocketService getGlobalWs() { return globalWs; }

    /** Register a callback that fires whenever the current user object changes. */
    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }

    /** Remove a previously registered callback. */
    public void removeChangeListener(Runnable listener) { changeListeners.remove(listener); }

    public boolean isLoggedIn() { return currentUser != null; }

    /** Clears the current session. */
    public void logout() {
        this.currentUser = null;
        NotificationManager.getInstance().clear();
    }

    /** Alias kept for source compatibility with older call sites. */
    public void logoutUser() { logout(); }
}
