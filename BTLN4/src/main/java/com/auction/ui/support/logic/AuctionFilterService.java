package com.auction.ui.support.logic;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.User;
import java.util.List;

public interface AuctionFilterService {
    List<User> filterUsers(List<User> users, String keyword, String role);

    List<Auction> filterAuctions(List<Auction> auctions, String keyword, String status, String category);

    boolean isValidForAdminAction(AuctionStatus status, String action);

    AuctionStatus statusAfterAdminAction(String action);
}
