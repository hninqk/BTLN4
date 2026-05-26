package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CurrencyUtilTest {

    @Test
    void parseCurrencySuccess() {
        assertEquals(1500000.0, CurrencyUtil.parseCurrency("1,500,000"));
        assertEquals(1500000.0, CurrencyUtil.parseCurrency("1.500.000"));
        assertEquals(1500000.0, CurrencyUtil.parseCurrency("1500000"));
        assertEquals(0.0, CurrencyUtil.parseCurrency(""));
        assertEquals(0.0, CurrencyUtil.parseCurrency(null));
    }

    @Test
    void testSetupWithNull() {
        assertDoesNotThrow(() -> CurrencyUtil.setupCurrencyTextField(null));
    }
}
