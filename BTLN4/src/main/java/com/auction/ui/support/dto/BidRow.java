package com.auction.ui.support.dto;

import com.auction.core.model.Auction;
import com.auction.core.model.BidTransaction;

public record BidRow(Auction auction, BidTransaction myBid, String result) {
}
