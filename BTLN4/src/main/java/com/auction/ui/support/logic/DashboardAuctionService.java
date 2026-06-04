package com.auction.ui.support.logic;

import com.auction.core.model.Auction;
import java.time.LocalDateTime;
import java.util.List;

public interface DashboardAuctionService {
    List<Auction> runningAuctions(List<Auction> auctions, LocalDateTime now);

    List<Auction> upcomingAuctions(List<Auction> auctions, LocalDateTime now);

    boolean isRunningByTime(Auction auction, LocalDateTime now);

    boolean isUpcomingByTimeOrStatus(Auction auction, LocalDateTime now);

    List<Auction> merge(List<Auction> first, List<Auction> second);

    int clampPage(int pageIndex, int itemCount, int pageSize);

    String formatCountdown(Auction auction, LocalDateTime now);
}
