package com.auction.core.util;

public final class BidLadderUtil {

    private BidLadderUtil() {

    }

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
