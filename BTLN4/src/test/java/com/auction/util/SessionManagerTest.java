package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.Bidder;
import com.auction.model.User;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    @Test
    void testListeners() {
        SessionManager sm = SessionManager.getInstance();
        final int[] count = {0};
        Runnable listener = () -> count[0]++;
        
        sm.addChangeListener(listener);
        sm.setCurrentUser(new Bidder("u2", "p2", 200.0));
        assertEquals(1, count[0]);
        
        sm.removeChangeListener(listener);
        sm.setCurrentUser(new Bidder("u3", "p3", 300.0));
        assertEquals(1, count[0]); // Should not increment
    }

    @Test
    void testLogoutUserAlias() {
        SessionManager sm = SessionManager.getInstance();
        sm.setCurrentUser(new Bidder("u", "p", 100.0));
        sm.logoutUser();
        assertNull(sm.getCurrentUser());
    }
}
