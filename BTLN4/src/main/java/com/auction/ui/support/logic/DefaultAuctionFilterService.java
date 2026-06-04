package com.auction.ui.support.logic;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.User;
import java.util.List;

public final class DefaultAuctionFilterService implements AuctionFilterService {
    @Override

    public List<User> filterUsers(List<User> users, String keyword, String role) {
        String normalizedKeyword = normalize(keyword);
        return users.stream()
                .filter(user -> {
                    boolean matchName = normalizedKeyword.isEmpty()
                            || user.getUsername().toLowerCase().contains(normalizedKeyword);
                    boolean matchRole = role == null || role.equals("Tất cả") || user.getRole().equals(role);
                    return matchName && matchRole;
                })
                .toList();
    }

    @Override

    public List<Auction> filterAuctions(List<Auction> auctions, String keyword, String status, String category) {
        String normalizedKeyword = normalize(keyword);
        return auctions.stream()
                .filter(auction -> {
                    boolean matchName = normalizedKeyword.isEmpty()
                            || auction.getItem().getName().toLowerCase().contains(normalizedKeyword)
                            || auction.getSeller().getUsername().toLowerCase().contains(normalizedKeyword);
                    boolean matchStatus = status == null || status.equals("Tất cả")
                            || auction.getStatusDisplay().equals(status);
                    boolean matchCategory = category == null || category.equals("Tất cả danh mục")
                            || category.equals("Tất cả")
                            || auction.getItem().getCategory().equals(category);
                    return matchName && matchStatus && matchCategory;
                })
                .toList();
    }

    @Override

    public boolean isValidForAdminAction(AuctionStatus status, String action) {
        return switch (action) {
            case "start" -> false;
            case "finish" -> status == AuctionStatus.RUNNING;
            case "cancel" -> status != AuctionStatus.CLOSED && status != AuctionStatus.CANCELED;
            default -> false;
        };
    }

    @Override

    public AuctionStatus statusAfterAdminAction(String action) {
        return switch (action) {
            case "finish" -> AuctionStatus.CLOSED;
            case "cancel" -> AuctionStatus.CANCELED;
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
