package com.auction.util;

/**
 * Utility to calculate dynamic bid increments (bước giá) based on the current
 * price.
 */
public final class BidLadderUtil {

    private BidLadderUtil() {
        // Prevent instantiation
    }

    /**
     * Returns the dynamic bid step (bước giá) for the given current highest bid.
     *
     * Scale (VND):
     * - Under 1M VND: step is 10k VND
     * - 1M VND to under 10M VND: step is 50k VND
     * - 10M VND to under 100M VND: step is 200k VND
     * - 100M VND to under 500M VND: step is 1M VND
     * - 500M VND to under 1B VND: step is 5M VND
     * - 1B VND to under 10B VND: step is 50M VND
     * - 10B VND to under 100B VND: step is 500M VND
     * - 100B VND and above: step is 5B VND
     */
    public static double getIncrementForPrice(double currentPrice) {
        if (currentPrice < 1_000_000) {
            return 10_000;
        } else if (currentPrice < 10_000_000) {
            return 50_000;
        } else if (currentPrice < 100_000_000) {
            return 200_000;
        } else if (currentPrice < 500_000_000) {
            return 1_000_000;
        } else if (currentPrice < 1_000_000_000) {
            return 5_000_000;
        } else if (currentPrice < 10_000_000_000.0) {
            return 50_000_000;
        } else if (currentPrice < 100_000_000_000.0) {
            return 500_000_000;
        } else {
            return 5_000_000_000.0;
        }
    }
}