package com.auction.ui.support.dto;

public record AdminStats(
        int totalUsers,
        int totalAuctions,
        long openAuctions,
        long runningAuctions,
        long finishedAuctions,
        long bidders,
        long sellers,
        long canceledAuctions) {
}
