package com.auction.ui.support.logic;

import com.auction.ui.support.dto.BidHistoryStats;
import com.auction.ui.support.dto.BidRow;
import com.auction.core.model.Auction;
import com.auction.core.model.Bidder;
import com.auction.service.AppFacade;

import java.util.List;

public interface BidHistoryService {
    void preload(List<Auction> auctions, String bidderId);

    void clearCache();

    BidHistoryStats getCachedStats(String bidderId);

    List<BidRow> fetchHistory(AppFacade app, Bidder bidder);

    BidHistoryStats calculateStats(List<BidRow> rows);

    List<BidRow> filter(List<BidRow> rows, String keyword, String resultFilter);
}
