package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.Auction;
import com.auction.model.AutoBid;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.repository.JdbcAutoBidRepository;
import com.auction.repository.JdbcUserRepository;

import java.time.LocalDateTime;
import java.util.List;

public class ProxyBiddingEngine {

    private final JdbcAutoBidRepository autoBidRepo;
    private final JdbcUserRepository userRepo;
    private final AuctionService auctionService;

    public ProxyBiddingEngine(JdbcAutoBidRepository autoBidRepo, JdbcUserRepository userRepo, AuctionService auctionService) {
        this.autoBidRepo = autoBidRepo;
        this.userRepo = userRepo;
        this.auctionService = auctionService;
    }

    /**
     * Resolves proxy bidding war.
     * Called whenever a new manual bid or new auto-bid is placed.
     */
    public AuctionService.AutoBidResult resolveBiddingWar(Auction auction) {
        AuctionService.AutoBidResult result = new AuctionService.AutoBidResult();
        boolean resolved = false;

        while (!resolved) {
            List<AutoBid> activeBids = autoBidRepo.findByAuctionId(auction.getId());
            double currentHighest = auction.getHighestBid();
            String currentWinnerId = auction.getWinner() != null ? auction.getWinner().getBidder().getId() : null;

            // Filter out auto-bids that can no longer beat the current highest bid
            List<AutoBid> validBids = new java.util.ArrayList<>();
            for (AutoBid ab : activeBids) {
                if (ab.getMaxBid() > currentHighest) {
                    validBids.add(ab);
                } else {
                    // Outbid or maxed out. Delete and unfreeze their maxBid!
                    autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), ab.getBidderId());
                    Bidder b = (Bidder) userRepo.findById(ab.getBidderId()).orElse(null);
                    if (b != null) {
                        b.unfreezeFunds(ab.getMaxBid());
                        userRepo.updateFrozenBalance(b.getId(), b.getFrozenBalance());
                        result.unfrozenBidders.add(b);
                    }
                }
            }

            if (validBids.isEmpty()) {
                break;
            }

            // Sort: highest max bid first. If tied, earliest created first.
            validBids.sort((a, b) -> {
                int cmp = Double.compare(b.getMaxBid(), a.getMaxBid());
                if (cmp == 0) {
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                }
                return cmp;
            });

            AutoBid top1 = validBids.get(0);
            Bidder top1Bidder = (Bidder) userRepo.findById(top1.getBidderId()).orElse(null);
            
            if (top1Bidder == null) {
                autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top1.getBidderId());
                continue;
            }

            if (validBids.size() == 1) {
                if (top1.getBidderId().equals(currentWinnerId)) {
                    // Top 1 is already winning, no need to outbid themselves
                    resolved = true;
                    break;
                }
                // Single proxy bidder against a manual bidder.
                // Outbid by exactly one increment, capped at max bid.
                double nextPrice = Math.min(currentHighest + top1.getIncrement(), top1.getMaxBid());
                
                try {
                    BidTransaction b = auctionService.placeBid(auction, top1Bidder, nextPrice);
                    Bidder unfrozen = auctionService.processOutbidUnfreeze();
                    
                    result.newBids.add(b);
                    if (unfrozen != null) result.unfrozenBidders.add(unfrozen);
                    result.virtualLogs.add(String.format("Hệ thống tự động đặt %,.0f ₫ cho %s", nextPrice, top1Bidder.getUsername()));
                    
                    resolved = true;
                } catch (Exception e) {
                    autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top1.getBidderId());
                    top1Bidder.unfreezeFunds(top1.getMaxBid());
                    userRepo.updateFrozenBalance(top1Bidder.getId(), top1Bidder.getFrozenBalance());
                    result.unfrozenBidders.add(top1Bidder);
                }
            } else {
                // Multiple proxy bidders
                AutoBid top2 = validBids.get(1);
                Bidder top2Bidder = (Bidder) userRepo.findById(top2.getBidderId()).orElse(null);
                
                if (top2Bidder == null) {
                    autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top2.getBidderId());
                    continue;
                }

                // They bid up to Top 2's max bid.
                // If top1 and top2 have the exact same maxBid, top1 wins (earlier creation).
                double targetBid = Math.min(top2.getMaxBid() + top1.getIncrement(), top1.getMaxBid());
                
                // If they are exactly equal in maxBid, top1 will just match top2's max bid (since targetBid would be capped at top1's maxBid).
                // Actually, if maxBids are equal, Top 1 matches the maxBid and wins because they placed it earlier.
                if (top1.getMaxBid() == top2.getMaxBid()) {
                    targetBid = top1.getMaxBid();
                }

                // Place the winning bid for Top 1
                try {
                    BidTransaction b = auctionService.placeBid(auction, top1Bidder, targetBid);
                    Bidder unfrozen = auctionService.processOutbidUnfreeze();
                    
                    result.newBids.add(b);
                    if (unfrozen != null) result.unfrozenBidders.add(unfrozen);
                    
                    result.virtualLogs.add(String.format("%s đã thắng cuộc chiến Auto-Bid với giá %,.0f ₫ (vượt qua %s)", 
                            top1Bidder.getUsername(), targetBid, top2Bidder.getUsername()));
                    
                    // Top 2's auto-bid is now exhausted (can't beat targetBid since targetBid >= top2.getMaxBid)
                    // We can optionally delete Top 2's autobid or just leave it. Leaving it is fine as it will be filtered out next iteration.
                    
                    resolved = true;
                } catch (Exception e) {
                    autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top1.getBidderId());
                    top1Bidder.unfreezeFunds(top1.getMaxBid());
                    userRepo.updateFrozenBalance(top1Bidder.getId(), top1Bidder.getFrozenBalance());
                    result.unfrozenBidders.add(top1Bidder);
                    continue;
                }
            }
        }
        return result;
    }
}
