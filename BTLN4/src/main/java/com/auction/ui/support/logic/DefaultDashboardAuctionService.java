package com.auction.ui.support.logic;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DefaultDashboardAuctionService implements DashboardAuctionService {
    @Override
    public List<Auction> runningAuctions(List<Auction> auctions, LocalDateTime now) {
        return auctions.stream()
                .filter(a -> isRunningByTime(a, now))
                .sorted(Comparator
                        .comparing((Auction a) -> endTimeOrMax(a))
                        .thenComparing(a -> a.getItem().getName()))
                .toList();
    }

    @Override
    public List<Auction> upcomingAuctions(List<Auction> auctions, LocalDateTime now) {
        return auctions.stream()
                .filter(a -> isUpcomingByTimeOrStatus(a, now))
                .sorted(Comparator
                        .comparing((Auction a) -> startTimeOrMax(a))
                        .thenComparing(a -> a.getItem().getName()))
                .toList();
    }

    @Override
    public boolean isRunningByTime(Auction auction, LocalDateTime now) {
        LocalDateTime start = auction.getStartTime();
        LocalDateTime end = auction.getEndTime();
        if (start == null || end == null || !now.isBefore(end)) {
            return false;
        }
        // Auction is running if status is RUNNING, OR if it's still marked as UPCOMING/OPEN but the startTime has arrived.
        return auction.getStatus() == AuctionStatus.RUNNING
                || (auction.getStatus() == AuctionStatus.UPCOMING || auction.getStatus() == AuctionStatus.OPEN) && !now.isBefore(start);
    }

    @Override
    public boolean isUpcomingByTimeOrStatus(Auction auction, LocalDateTime now) {
        LocalDateTime end = auction.getEndTime();
        if (end != null && !now.isBefore(end)) {
            return false;
        }
        LocalDateTime start = auction.getStartTime();
        if (start == null) {
            return auction.getStatus() == AuctionStatus.UPCOMING || auction.getStatus() == AuctionStatus.OPEN;
        }
        // Auction is upcoming only if it's still in UPCOMING/OPEN status AND the startTime is still in the future.
        return (auction.getStatus() == AuctionStatus.UPCOMING || auction.getStatus() == AuctionStatus.OPEN)
                && start.isAfter(now);
    }

    @Override
    public List<Auction> merge(List<Auction> first, List<Auction> second) {
        ArrayList<Auction> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    @Override
    public int clampPage(int pageIndex, int itemCount, int pageSize) {
        int maxPage = Math.max(0, (int) Math.ceil(itemCount / (double) pageSize) - 1);
        return Math.max(0, Math.min(pageIndex, maxPage));
    }

    @Override
    public String formatCountdown(Auction auction, LocalDateTime now) {
        if (auction.getStartTime() != null && auction.getStartTime().isAfter(now)) {
            return formatDuration("Còn", Duration.between(now, auction.getStartTime()));
        }
        if (auction.getEndTime() == null) {
            return "Chưa có hạn kết thúc";
        }

        Duration remaining = Duration.between(now, auction.getEndTime());
        if (remaining.isNegative() || remaining.isZero()) {
            return "Sắp kết thúc";
        }
        return formatDuration("Còn", remaining);
    }

    private LocalDateTime endTimeOrMax(Auction auction) {
        return auction.getEndTime() == null ? LocalDateTime.MAX : auction.getEndTime();
    }

    private LocalDateTime startTimeOrMax(Auction auction) {
        return auction.getStartTime() == null ? LocalDateTime.MAX : auction.getStartTime();
    }

    private String formatDuration(String prefix, Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (days > 0) {
            return String.format("%s %dd %02dh %02dm", prefix, days, hours, minutes);
        }
        return String.format("%s %02d:%02d:%02d", prefix, duration.toHours(), minutes, seconds);
    }
}
