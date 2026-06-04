package com.auction.ui.support.dto;

import com.auction.core.model.Auction;
import com.auction.core.model.BidTransaction;

import java.util.List;

public record BidHistoryStats(
        List<BidRow> rows,
        String totalBids,
        String won,
        String active,
        String spent) {
}
