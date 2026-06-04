package com.auction.ui.support.logic;

import com.auction.ui.support.dto.ProfileStats;
import com.auction.core.model.Auction;
import com.auction.core.model.Bidder;
import com.auction.core.model.Seller;
import com.auction.service.AppFacade;
import java.util.List;

public interface ProfileStatsService {
    void preloadBidCount(List<Auction> auctions, String bidderId);

    void clearBidCountCache();

    Long cachedBidCount(String bidderId);

    long countBids(AppFacade app, Bidder bidder);

    int countSellerAuctions(AppFacade app, Seller seller);

    ProfileStats calculateSellerStats(AppFacade app, Seller seller);
}
