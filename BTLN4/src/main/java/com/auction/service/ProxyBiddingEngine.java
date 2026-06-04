package com.auction.service;

import com.auction.core.exception.InvalidBidException;
import com.auction.core.model.Auction;
import com.auction.core.model.AutoBid;
import com.auction.core.model.BidTransaction;
import com.auction.core.model.Bidder;
import com.auction.core.util.BidLadderUtil;
import com.auction.infra.repository.JdbcAutoBidRepository;
import com.auction.infra.repository.JdbcUserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxyBiddingEngine {

    private final JdbcAutoBidRepository autoBidRepo;

    private final JdbcUserRepository userRepo;

    private final BiddingService biddingService;

    public ProxyBiddingEngine(
            JdbcAutoBidRepository autoBidRepo,
            JdbcUserRepository userRepo,
            BiddingService biddingService) {
        this.autoBidRepo = autoBidRepo;
        this.userRepo = userRepo;
        this.biddingService = biddingService;
    }

    public AuctionService.AutoBidResult resolveBiddingWar(Auction auction) {
        AuctionService.AutoBidResult result = new AuctionService.AutoBidResult();
        Map<String, Bidder> unfrozenMap = new HashMap<>();

        List<AutoBid> activeBids = new ArrayList<>(autoBidRepo.findByAuctionId(auction.getId()));

        int maxIterations = Math.max(activeBids.size() * 3 + 10, 50);
        int iterations = 0;

        while (iterations++ < maxIterations) {
            double currentHighest = auction.getHighestBid();
            String currentWinnerId = auction.getWinner() != null ? auction.getWinner().getBidder().getId() : null;

            List<AutoBid> validBids = removeExhaustedBids(auction, activeBids, currentHighest, unfrozenMap);
            if (validBids.isEmpty()) break;

            sortBidsByMaxDescAndTime(validBids);
            AutoBid top1 = validBids.get(0);

            if (validBids.size() == 1 && top1.getBidderId().equals(currentWinnerId)) {
                break;
            }

            Bidder top1Bidder = (Bidder) userRepo.findById(top1.getBidderId()).orElse(null);
            if (top1Bidder == null) {
                deactivateAutoBid(top1, auction, activeBids, result, unfrozenMap, null);
                continue;
            }

            double targetBid;
            String logMsg;

            if (validBids.size() == 1) {
                targetBid = calculateSingleChallengerBid(currentHighest, top1);
                logMsg = String.format("[Auto-Bid] %s tự động đặt %,.0f ₫", top1Bidder.getUsername(), targetBid);
            } else {
                AutoBid top2 = validBids.get(1);
                Bidder top2Bidder = (Bidder) userRepo.findById(top2.getBidderId()).orElse(null);

                if (top2Bidder == null) {
                    deactivateAutoBid(top2, auction, activeBids, result, unfrozenMap, null);
                    continue;
                }

                handleTop2Challenge(auction, top2, top2Bidder, currentHighest, currentWinnerId, activeBids, result, unfrozenMap);

                targetBid = calculateMultipleChallengersBid(top1, top2);
                logMsg = String.format("[Auto-Bid] %s vượt qua %s với giá %,.0f ₫",
                        top1Bidder.getUsername(), top2Bidder.getUsername(), targetBid);

                if (targetBid >= top2.getMaxBid()) {
                    deactivateAutoBid(top2, auction, activeBids, result, unfrozenMap, top2Bidder);
                }
            }

            if (targetBid <= auction.getHighestBid()) break;

            BidResult bidResult = placeTop1Bid(auction, top1, top1Bidder, targetBid, logMsg, activeBids, result, unfrozenMap);
            if (bidResult == BidResult.ALREADY_WON) {
                break;
            }
        }

        result.unfrozenBidders.addAll(unfrozenMap.values());
        return result;
    }

    private enum BidResult { SUCCESS, ALREADY_WON, FAILED }

    private void deactivateAutoBid(AutoBid ab, Auction auction, List<AutoBid> activeBids,
                                   AuctionService.AutoBidResult result, Map<String, Bidder> unfrozenMap,
                                   Bidder knownBidder) {
        autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), ab.getBidderId());
        activeBids.remove(ab);
        if (result != null) {
            result.deactivatedBidderIds.add(ab.getBidderId());
        }

        Bidder b = knownBidder != null ? knownBidder : (Bidder) userRepo.findById(ab.getBidderId()).orElse(null);
        if (b != null) {
            b.unfreezeFunds(ab.getMaxBid());
            userRepo.updateFrozenBalance(b.getId(), b.getFrozenBalance());
            if (unfrozenMap != null) {
                unfrozenMap.put(b.getId(), b);
            }
        }
    }

    private List<AutoBid> removeExhaustedBids(Auction auction, List<AutoBid> activeBids, double currentHighest, Map<String, Bidder> unfrozenMap) {
        List<AutoBid> validBids = new ArrayList<>();
        List<AutoBid> exhausted = new ArrayList<>();
        for (AutoBid ab : activeBids) {
            if (ab.getMaxBid() > currentHighest) validBids.add(ab);
            else                                  exhausted.add(ab);
        }

        for (AutoBid ab : exhausted) {
            deactivateAutoBid(ab, auction, activeBids, null, unfrozenMap, null);
        }
        return validBids;
    }

    private void sortBidsByMaxDescAndTime(List<AutoBid> validBids) {
        validBids.sort((a, b) -> {
            int cmp = Double.compare(b.getMaxBid(), a.getMaxBid());
            return cmp != 0 ? cmp : a.getCreatedAt().compareTo(b.getCreatedAt());
        });
    }

    private double calculateSingleChallengerBid(double currentHighest, AutoBid top1) {
        double step = BidLadderUtil.getIncrementForPrice(currentHighest);
        return Math.min(currentHighest + step, top1.getMaxBid());
    }

    private double calculateMultipleChallengersBid(AutoBid top1, AutoBid top2) {
        if (Double.compare(top1.getMaxBid(), top2.getMaxBid()) == 0) {
            return top1.getMaxBid();
        }
        double step = BidLadderUtil.getIncrementForPrice(top2.getMaxBid());
        return Math.min(top2.getMaxBid() + step, top1.getMaxBid());
    }

    private void handleTop2Challenge(Auction auction, AutoBid top2, Bidder top2Bidder,
                                     double currentHighest, String currentWinnerId,
                                     List<AutoBid> activeBids, AuctionService.AutoBidResult result,
                                     Map<String, Bidder> unfrozenMap) {
        if (top2.getMaxBid() > currentHighest && !top2.getBidderId().equals(currentWinnerId)) {
            try {
                BidTransaction b2 = biddingService.placeBid(auction, top2Bidder, top2.getMaxBid());
                Bidder unfrozen2 = biddingService.processOutbidUnfreeze();
                result.newBids.add(b2);
                if (unfrozen2 != null) unfrozenMap.put(unfrozen2.getId(), unfrozen2);
                result.virtualLogs.add(String.format("[Auto-Bid] %s tự động đẩy giá lên %,.0f ₫",
                        top2Bidder.getUsername(), top2.getMaxBid()));
            } catch (Exception e) {

                deactivateAutoBid(top2, auction, activeBids, result, unfrozenMap, top2Bidder);
            }
        }
    }

    private BidResult placeTop1Bid(Auction auction, AutoBid top1, Bidder top1Bidder, double targetBid, String logMsg,
                                 List<AutoBid> activeBids, AuctionService.AutoBidResult result, Map<String, Bidder> unfrozenMap) {
        try {
            BidTransaction b = biddingService.placeBid(auction, top1Bidder, targetBid);
            Bidder unfrozen = biddingService.processOutbidUnfreeze();
            result.newBids.add(b);
            if (unfrozen != null) unfrozenMap.put(unfrozen.getId(), unfrozen);
            result.virtualLogs.add(logMsg);
            return BidResult.SUCCESS;
        } catch (InvalidBidException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("đang là người ra giá cao nhất")) {

                return BidResult.ALREADY_WON;
            }

            deactivateAutoBid(top1, auction, activeBids, result, unfrozenMap, top1Bidder);
            return BidResult.FAILED;
        } catch (Exception e) {
            System.err.println("[ProxyBiddingEngine] Unexpected error: " + e.getMessage());
            deactivateAutoBid(top1, auction, activeBids, result, unfrozenMap, top1Bidder);
            return BidResult.FAILED;
        }
    }
}
