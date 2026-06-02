package com.auction.ui.support.dto;

import com.auction.core.model.Auction;

public record AuctionSnapshot(Auction auction, int bidCount) {
}
