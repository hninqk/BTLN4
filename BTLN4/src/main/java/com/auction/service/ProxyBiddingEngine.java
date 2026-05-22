package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.AutoBid;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.repository.JdbcAutoBidRepository;
import com.auction.repository.JdbcUserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProxyBiddingEngine – resolves automated bidding wars following industry-standard proxy rules.
 *
 * Algorithm:
 *   1. Fetch all active auto-bids for the auction (once).
 *   2. Per iteration: filter exhausted bids, sort by maxBid desc (tie → earliest first).
 *   3. If top1 is already the current winner → done.
 *   4. Single challenger: top1 jumps one increment above current price (capped at maxBid).
 *   5. Multiple challengers: top1 jumps one increment above top2's maxBid (capped at top1's maxBid).
 *   6. On "already highest bidder" exception → race condition, just stop (don't delete the auto-bid).
 *   7. On real error (insufficient funds) → invalidate the auto-bid.
 *   8. Safety cap: MAX_ITERATIONS prevents infinite loops.
 */
public class ProxyBiddingEngine {

    private final JdbcAutoBidRepository autoBidRepo;
    private final JdbcUserRepository userRepo;
    private final AuctionService auctionService;

    public ProxyBiddingEngine(
            JdbcAutoBidRepository autoBidRepo,
            JdbcUserRepository userRepo,
            AuctionService auctionService) {
        this.autoBidRepo = autoBidRepo;
        this.userRepo = userRepo;
        this.auctionService = auctionService;
    }

    /** Entry point – call after any manual bid or new auto-bid registration. */
    public AuctionService.AutoBidResult resolveBiddingWar(Auction auction) {
        AuctionService.AutoBidResult result = new AuctionService.AutoBidResult();
        Map<String, Bidder> unfrozenMap = new HashMap<>();

        // Fetch autobids ONCE to avoid redundant DB round-trips
        List<AutoBid> activeBids = new ArrayList<>(autoBidRepo.findByAuctionId(auction.getId()));

        // Safety cap: prevents an edge-case infinite loop
        int maxIterations = Math.max(activeBids.size() * 3 + 10, 50);
        int iterations = 0;

        while (iterations++ < maxIterations) {

            double currentHighest = auction.getHighestBid();
            String currentWinnerId = auction.getWinner() != null
                    ? auction.getWinner().getBidder().getId() : null;

            // ── 1. Partition: valid (can still outbid) vs exhausted ──────────
            List<AutoBid> validBids = new ArrayList<>();
            List<AutoBid> exhausted = new ArrayList<>();
            for (AutoBid ab : activeBids) {
                if (ab.getMaxBid() > currentHighest) validBids.add(ab);
                else                                  exhausted.add(ab);
            }

            // Clean up exhausted auto-bids and unfreeze their funds
            for (AutoBid ab : exhausted) {
                activeBids.remove(ab);
                autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), ab.getBidderId());
                Bidder b = (Bidder) userRepo.findById(ab.getBidderId()).orElse(null);
                if (b != null) {
                    b.unfreezeFunds(ab.getMaxBid());
                    userRepo.updateFrozenBalance(b.getId(), b.getFrozenBalance());
                    unfrozenMap.put(b.getId(), b);
                }
            }

            if (validBids.isEmpty()) break;

            // ── 2. Sort: highest maxBid first; ties → earliest placed wins ───
            validBids.sort((a, b) -> {
                int cmp = Double.compare(b.getMaxBid(), a.getMaxBid());
                return cmp != 0 ? cmp : a.getCreatedAt().compareTo(b.getCreatedAt());
            });

            AutoBid top1 = validBids.get(0);

            // ── 3. KEY FIX: top1 is already current winner AND no one can challenge ────
            if (validBids.size() == 1 && top1.getBidderId().equals(currentWinnerId)) {
                break;
            }

            Bidder top1Bidder = (Bidder) userRepo.findById(top1.getBidderId()).orElse(null);
            if (top1Bidder == null) {
                autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top1.getBidderId());
                activeBids.remove(top1);
                result.deactivatedBidderIds.add(top1.getBidderId());
                continue;
            }

            // ── 4 & 5. Calculate target bid ──────────────────────────────────
            double targetBid;
            String logMsg;

            if (validBids.size() == 1) {
                // Single proxy bidder vs a manual high bid
                targetBid = Math.min(currentHighest + top1.getIncrement(), top1.getMaxBid());
                logMsg = String.format("[Auto-Bid] %s tự động đặt %,.0f ₫",
                        top1Bidder.getUsername(), targetBid);
            } else {
                // Multiple proxy bidders: top1 jumps above top2's ceiling
                AutoBid top2 = validBids.get(1);
                Bidder top2Bidder = (Bidder) userRepo.findById(top2.getBidderId()).orElse(null);
                if (top2Bidder == null) {
                    autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top2.getBidderId());
                    activeBids.remove(top2);
                    result.deactivatedBidderIds.add(top2.getBidderId());
                    continue;
                }

                if (top2.getMaxBid() > currentHighest && !top2.getBidderId().equals(currentWinnerId)) {
                    // Let top2 place its max bid to defend/challenge and create history
                    try {
                        BidTransaction b2 = auctionService.placeBid(auction, top2Bidder, top2.getMaxBid());
                        Bidder unfrozen2 = auctionService.processOutbidUnfreeze();
                        result.newBids.add(b2);
                        if (unfrozen2 != null) unfrozenMap.put(unfrozen2.getId(), unfrozen2);
                        result.virtualLogs.add(String.format("[Auto-Bid] %s tự động đẩy giá lên %,.0f ₫", 
                                top2Bidder.getUsername(), top2.getMaxBid()));
                        currentHighest = top2.getMaxBid(); // Update for top1's logic
                    } catch (Exception e) {
                        // If it fails (e.g. balance issues), remove top2 and let loop continue
                        autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top2.getBidderId());
                        activeBids.remove(top2);
                        result.deactivatedBidderIds.add(top2.getBidderId());
                        top2Bidder.unfreezeFunds(top2.getMaxBid());
                        userRepo.updateFrozenBalance(top2Bidder.getId(), top2Bidder.getFrozenBalance());
                        unfrozenMap.put(top2Bidder.getId(), top2Bidder);
                        continue;
                    }
                }

                if (Double.compare(top1.getMaxBid(), top2.getMaxBid()) == 0) {
                    targetBid = top1.getMaxBid();    // Tie: top1 wins (placed first)
                } else {
                    targetBid = Math.min(top2.getMaxBid() + top1.getIncrement(), top1.getMaxBid());
                }
                logMsg = String.format("[Auto-Bid] %s vượt qua %s với giá %,.0f ₫",
                        top1Bidder.getUsername(), top2Bidder.getUsername(), targetBid);

                // Pre-remove top2 if this bid exhausts it
                if (targetBid >= top2.getMaxBid()) {
                    autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top2.getBidderId());
                    activeBids.remove(top2);
                    result.deactivatedBidderIds.add(top2.getBidderId());
                    top2Bidder.unfreezeFunds(top2.getMaxBid());
                    userRepo.updateFrozenBalance(top2Bidder.getId(), top2Bidder.getFrozenBalance());
                    unfrozenMap.put(top2Bidder.getId(), top2Bidder);
                }
            }

            // Guard: target must actually outbid current price
            if (targetBid <= currentHighest) break;

            // ── 6 & 7. Place the bid ─────────────────────────────────────────
            try {
                BidTransaction b = auctionService.placeBid(auction, top1Bidder, targetBid);
                Bidder unfrozen = auctionService.processOutbidUnfreeze();
                result.newBids.add(b);
                if (unfrozen != null) unfrozenMap.put(unfrozen.getId(), unfrozen);
                result.virtualLogs.add(logMsg);
                // Loop continues → top1 may still need to beat top3, top4…

            } catch (InvalidBidException e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("đang là người ra giá cao nhất")) {
                    // Benign race condition: top1 already won. Stop, don't touch auto-bid.
                    break;
                }
                // Real error (e.g., insufficient funds): invalidate top1's auto-bid
                autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top1.getBidderId());
                activeBids.remove(top1);
                result.deactivatedBidderIds.add(top1.getBidderId());
                top1Bidder.unfreezeFunds(top1.getMaxBid());
                userRepo.updateFrozenBalance(top1Bidder.getId(), top1Bidder.getFrozenBalance());
                unfrozenMap.put(top1Bidder.getId(), top1Bidder);

            } catch (Exception e) {
                // Unexpected error: remove top1 to prevent infinite loop
                System.err.println("[ProxyBiddingEngine] Unexpected error: " + e.getMessage());
                autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), top1.getBidderId());
                activeBids.remove(top1);
                result.deactivatedBidderIds.add(top1.getBidderId());
            }
        }

        result.unfrozenBidders.addAll(unfrozenMap.values());
        return result;
    }
}
