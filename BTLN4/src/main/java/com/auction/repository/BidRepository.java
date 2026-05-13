package com.auction.repository;

import com.auction.model.BidTransaction;

import java.util.List;

public interface BidRepository {
    void save(BidTransaction tx);
}