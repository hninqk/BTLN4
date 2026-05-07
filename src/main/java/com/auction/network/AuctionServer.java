package com.auction.network;

import io.javalin.Javalin;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.repository.JdbcBidRepository;

public class AuctionServer {
    private final JdbcBidRepository repository = new JdbcBidRepository();

    public void start() {
        Javalin app = Javalin.create().start(7000);
        app.post("/bid", ctx -> {
            BidTransaction tx = ctx.bodyAsClass(BidTransaction.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(tx.getAuction().getId());

            if (auction != null) {
                try {
                    auction.placeBid(tx);
                    repository.save(tx);
                    ctx.status(201).json(tx);
                } catch (Exception e) {
                    ctx.status(400).result(e.getMessage());
                }
            } else {
                ctx.status(404).result("Auction not found");
            }
        });
    }

    public static void main(String[] args) {
        new AuctionServer().start();
    }
}