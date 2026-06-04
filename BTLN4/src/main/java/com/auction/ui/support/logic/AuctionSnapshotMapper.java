package com.auction.ui.support.logic;

import com.auction.ui.support.dto.AuctionSnapshot;
import com.auction.core.model.Auction;
import com.auction.core.model.Seller;
import com.google.gson.JsonObject;
import java.util.Optional;

public interface AuctionSnapshotMapper {
    Optional<AuctionSnapshot> fromServerSnapshot(JsonObject json);

    Optional<Auction> fromSellerSnapshot(JsonObject json, Seller seller);
}
