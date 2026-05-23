package com.auction.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHashServiceTest {

    @Test
    void hashUsesArgon2idAndVerifiesOriginalPasswordOnly() {
        String hash = PasswordHashService.hash("correct horse battery staple");

        assertTrue(hash.startsWith("$argon2id$v=19$"));
        assertTrue(PasswordHashService.verify(hash, "correct horse battery staple"));
        assertFalse(PasswordHashService.verify(hash, "wrong password"));
    }

    @Test
    void samePasswordReceivesUniqueSalt() {
        String first = PasswordHashService.hash("password123");
        String second = PasswordHashService.hash("password123");

        assertNotEquals(first, second);
        assertTrue(PasswordHashService.verify(first, "password123"));
        assertTrue(PasswordHashService.verify(second, "password123"));
    }
}
