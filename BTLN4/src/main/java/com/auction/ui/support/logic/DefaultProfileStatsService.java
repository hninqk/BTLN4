package com.auction.ui.support.logic;

import com.auction.ui.support.dto.ProfileStats;
import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.Bidder;
import com.auction.core.model.Seller;
import com.auction.service.AppFacade;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultProfileStatsService implements ProfileStatsService {
    private static final Map<String, Long> BID_COUNT_CACHE = new ConcurrentHashMap<>();

    @Override

    public void preloadBidCount(List<Auction> auctions, String bidderId) {
        long count = 0;
        for (Auction full : auctions) {
            for (com.auction.core.model.BidTransaction bid : full.getBidHistory()) {
                if (bid.getBidder().getId().equals(bidderId)) {
                    count++;
                }
            }
        }
        BID_COUNT_CACHE.put(bidderId, count);
    }

    @Override

    public void clearBidCountCache() {
        BID_COUNT_CACHE.clear();
    }

    @Override

    public Long cachedBidCount(String bidderId) {
        return BID_COUNT_CACHE.get(bidderId);
    }

    @Override

    public long countBids(AppFacade app, Bidder bidder) {
        long count = 0;
        for (Auction shallow : app.getAllAuctions()) {
            Auction full = app.findAuctionById(shallow.getId()).orElse(null);
            if (full != null) {
                count += full.getBidHistory().stream()
                        .filter(bid -> bid.getBidder().getId().equals(bidder.getId()))
                        .count();
            }
        }
        BID_COUNT_CACHE.put(bidder.getId(), count);
        return count;
    }

    @Override

    public int countSellerAuctions(AppFacade app, Seller seller) {
        return app.getAuctionsBySeller(seller).size();
    }

    @Override

    public ProfileStats calculateSellerStats(AppFacade app, Seller seller) {
        List<Auction> auctions = app.getAuctionsBySeller(seller);
        long closed = auctions.stream()
                .filter(a -> a.getStatus() == AuctionStatus.CLOSED)
                .count();
        double revenue = auctions.stream()
                .filter(a -> a.getStatus() == AuctionStatus.CLOSED)
                .mapToDouble(Auction::getHighestBid)
                .sum();
        return new ProfileStats(closed, revenue);
    }
}
