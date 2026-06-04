package com.auction.ui.support.logic;

import com.auction.ui.support.dto.BidHistoryStats;
import com.auction.ui.support.dto.BidRow;
import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.BidTransaction;
import com.auction.core.model.Bidder;
import com.auction.service.AppFacade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class DefaultBidHistoryService implements BidHistoryService {
    private static final Map<String, BidHistoryStats> CACHE = new ConcurrentHashMap<>();

    @Override
    public void preload(List<Auction> auctions, String bidderId) {
        List<BidRow> rows = new ArrayList<>();
        for (Auction full : auctions) {
            BidTransaction myBid = null;
            for (BidTransaction b : full.getBidHistory()) {
                if (b.getBidder().getId().equals(bidderId)) {
                    myBid = b;
                }
            }
            if (myBid == null)
                continue;

            rows.add(new BidRow(full, myBid, determineResult(full, bidderId)));
        }
        CACHE.put(bidderId, calculateStats(rows));
    }

    @Override
    public void clearCache() {
        CACHE.clear();
    }

    @Override
    public BidHistoryStats getCachedStats(String bidderId) {
        return CACHE.get(bidderId);
    }

    @Override
    public List<BidRow> fetchHistory(AppFacade app, Bidder bidder) {
        List<BidRow> rows = new ArrayList<>();
        List<Auction> shallowAuctions = app.getPublicAuctions();
        for (Auction shallow : shallowAuctions) {
            Auction full = app.findAuctionById(shallow.getId()).orElse(null);
            if (full == null)
                continue;

            Optional<BidTransaction> myLatest = full.getBidHistory().stream()
                    .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                    .reduce((first, second) -> second);

            if (myLatest.isEmpty())
                continue;

            rows.add(new BidRow(full, myLatest.get(), determineResult(full, bidder.getId())));
        }
        return rows;
    }

    @Override
    public BidHistoryStats calculateStats(List<BidRow> rows) {
        long won = 0, active = 0;
        double totalSpent = 0;
        for (BidRow r : rows) {
            if (r.result().contains("Thắng")) {
                won++;
                totalSpent += r.myBid().getAmount();
            } else if (r.result().equals("Đang tham gia")) {
                active++;
            }
        }
        return new BidHistoryStats(
                rows,
                String.valueOf(rows.size()),
                String.valueOf(won),
                String.valueOf(active),
                String.format("%,.0f ₫", totalSpent));
    }

    @Override
    public List<BidRow> filter(List<BidRow> rows, String keyword, String resultFilter) {
        String kw = (keyword == null) ? "" : keyword.trim().toLowerCase();
        return rows.stream()
                .filter(r -> {
                    boolean matchName = kw.isEmpty()
                            || r.auction().getItem().getName().toLowerCase().contains(kw)
                            || r.auction().getSeller().getUsername().toLowerCase().contains(kw);
                    boolean matchResult = resultFilter == null || resultFilter.equals("Tất cả")
                            || r.result().equals(resultFilter);
                    return matchName && matchResult;
                }).collect(Collectors.toList());
    }

    private String determineResult(Auction auction, String bidderId) {
        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.RUNNING || status == AuctionStatus.UPCOMING || status == AuctionStatus.OPEN) {
            return "Đang tham gia";
        } else {
            BidTransaction winner = auction.getWinner();
            if (winner != null && winner.getBidder().getId().equals(bidderId)) {
                return "Thắng";
            } else {
                return "Thua";
            }
        }
    }
}
