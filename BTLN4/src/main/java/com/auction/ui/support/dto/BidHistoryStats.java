package com.auction.ui.support.dto;

import java.util.List;

public record BidHistoryStats(
        List<BidRow> rows,
        String totalBids,
        String won,
        String active,
        String spent) {
}
