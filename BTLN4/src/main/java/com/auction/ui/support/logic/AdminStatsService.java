package com.auction.ui.support.logic;

import com.auction.ui.support.dto.AdminStats;
import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.Bidder;
import com.auction.core.model.Seller;
import com.auction.core.model.User;

import java.util.List;

public interface AdminStatsService {
    AdminStats calculate(List<User> users, List<Auction> auctions);

    final class Default implements AdminStatsService {
        @Override
        public AdminStats calculate(List<User> users, List<Auction> auctions) {
            return new AdminStats(
                    users.size(),
                    auctions.size(),
                    auctions.stream()
                            .filter(a -> a.getStatus() == AuctionStatus.UPCOMING || a.getStatus() == AuctionStatus.OPEN)
                            .count(),
                    auctions.stream().filter(a -> a.getStatus() == AuctionStatus.RUNNING).count(),
                    auctions.stream().filter(a -> a.getStatus() == AuctionStatus.CLOSED).count(),
                    users.stream().filter(u -> u instanceof Bidder).count(),
                    users.stream().filter(u -> u instanceof Seller).count(),
                    auctions.stream().filter(a -> a.getStatus() == AuctionStatus.CANCELED).count());
        }
    }
}
