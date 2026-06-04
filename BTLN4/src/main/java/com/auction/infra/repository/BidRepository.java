package com.auction.infra.repository;

import com.auction.core.model.BidTransaction;

import java.util.List;

public interface BidRepository {
    void save(BidTransaction tx);
}