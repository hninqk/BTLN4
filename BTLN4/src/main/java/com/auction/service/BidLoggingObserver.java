package com.auction.service;

import com.auction.core.model.BidTransaction;
import com.auction.core.model.Observer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lắng nghe các lượt đặt giá thành công trên một phiên đấu giá.
 * Áp dụng Design Pattern Observer từ model.
 */
public class BidLoggingObserver implements Observer {
    private static final Logger log = LoggerFactory.getLogger(BidLoggingObserver.class);

    @Override
    public void update(BidTransaction newBid) {
        log.info("[Observer] Lượt Bid mới: {} VND được đặt bởi user '{}' trên phiên '{}'", 
                newBid.getAmount(), 
                newBid.getBidder().getUsername(), 
                newBid.getAuction().getId());
    }
}
