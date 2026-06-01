package com.auction.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDateTime;
import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;

public class Auction extends Entity implements Subject {
    private final Seller seller;
    private final Item item;
    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double highestBid;
    private List<BidTransaction> bidHistory = new CopyOnWriteArrayList<>();
    private List<AutoBid> autoBids = new CopyOnWriteArrayList<>();
    private List<Observer> observers = new CopyOnWriteArrayList<>();

    /**
     * Legacy constructor: create a live auction immediately.
     */
    public Auction(Seller seller, Item item, LocalDateTime endTime) {
        this(seller, item, com.auction.util.TimeSyncManager.getNow(), endTime);
    }

    /**
     * Seller creates a scheduled auction. If startTime has arrived on the server,
     * it is live immediately; otherwise it waits in UPCOMING.
     */
    public Auction(Seller seller, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super();
        this.seller = seller;
        this.item = item;
        this.highestBid = (item != null) ? item.getStartingPrice() : 0.0;
        this.startTime = startTime;
        this.endTime = endTime;
        LocalDateTime now = com.auction.util.TimeSyncManager.getNow();
        this.status = startTime != null && !startTime.isAfter(now)
                ? AuctionStatus.RUNNING
                : AuctionStatus.UPCOMING;
    }

    /** DB reconstruction constructor */
    public Auction(String id, LocalDateTime createdAt, Seller seller, Item item,
            AuctionStatus status, double highestBid,
            LocalDateTime startTime, LocalDateTime endTime) {
        super(id, createdAt);
        this.seller = seller;
        this.item = item;
        this.status = status;
        this.highestBid = highestBid;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ------- Observer pattern -------

    @Override
    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObserver(BidTransaction newBid) {
        for (Observer o : observers)
            o.update(newBid);
    }

    // ------- State transitions -------

    /** Legacy approval path: PENDING/OPEN → UPCOMING. */
    public void approveAuction() throws InvalidStatusException {
        if (this.status != AuctionStatus.PENDING && this.status != AuctionStatus.OPEN)
            throw new InvalidStatusException("Chỉ có thể duyệt phiên đang chờ. Trạng thái hiện tại: " + status);
        status = AuctionStatus.UPCOMING;
    }

    /** Legacy/manual start entry point retained for internal compatibility. */
    public void startAuction() throws InvalidStatusException {
        goLive();
    }

    /** Server scheduler promotes a seller-scheduled auction to RUNNING. */
    public void goLive() throws InvalidStatusException {
        if (this.status != AuctionStatus.UPCOMING && this.status != AuctionStatus.OPEN)
            throw new InvalidStatusException("Chỉ có thể bắt đầu phiên sắp diễn ra. Trạng thái hiện tại: " + status);
        if (this.startTime == null)
            this.startTime = com.auction.util.TimeSyncManager.getNow();
        status = AuctionStatus.RUNNING;
    }

    public synchronized void placeBid(BidTransaction newBid) throws InvalidBidException, InvalidStatusException {
        if (this.status != AuctionStatus.RUNNING)
            throw new InvalidStatusException("Phiên đấu giá chưa bắt đầu hoặc đã kết thúc.");
        if (this.endTime != null && com.auction.util.TimeSyncManager.getNow().isAfter(this.endTime))
            throw new InvalidStatusException("Phiên đấu giá đã kết thúc.");
        double newBidAmount = newBid.getAmount();
        double step = com.auction.util.BidLadderUtil.getIncrementForPrice(this.highestBid);
        if (newBidAmount < this.highestBid + step) {
            throw new InvalidBidException(String.format("Giá đặt tối thiểu phải là %,.0f ₫ (bước giá %,.0f ₫).", this.highestBid + step, step));
        }
        this.highestBid = newBidAmount;
        this.bidHistory.add(newBid);
        this.notifyObserver(newBid);
    }

    public void finishAuction() throws InvalidStatusException {
        if (status != AuctionStatus.RUNNING)
            throw new InvalidStatusException("Chỉ có thể kết thúc phiên đang chạy. Trạng thái hiện tại: " + status);
        status = AuctionStatus.CLOSED;
    }

    public void cancelAuction() throws InvalidStatusException {
        if (status == AuctionStatus.CLOSED)
            throw new InvalidStatusException("Không thể huỷ phiên đã đóng.");
        if (status == AuctionStatus.CANCELED)
            throw new InvalidStatusException("Phiên đấu giá đã bị huỷ rồi.");
        status = AuctionStatus.CANCELED;
    }

    // ------- Getters -------

    public Seller getSeller() {
        return seller;
    }

    public Item getItem() {
        return item;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public double getHighestBid() {
        return highestBid;
    }

    public void setHighestBid(double highestBid) {
        this.highestBid = highestBid;
    }

    /**
     * Direct status setter for WebSocket sync only.
     * Bypasses state-machine guards — use ONLY to apply a server-broadcast status.
     * Business logic MUST still go through approveAuction/startAuction/etc.
     */
    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    /**
     * Direct startTime setter for WS sync (applied when server broadcasts RUNNING
     * state).
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public List<BidTransaction> getBidHistory() {
        return new java.util.ArrayList<>(bidHistory);
    }

    /**
     * DB-reconstruction only — injects a bid directly without status/amount checks.
     * Used by JdbcAuctionRepository.loadBidHistory() to restore persisted bids.
     * DO NOT call this from business logic; use placeBid() instead.
     */
    public void injectBid(BidTransaction bid) {
        bidHistory.add(bid);
    }

    public void injectAutoBid(AutoBid autoBid) {
        autoBids.add(autoBid);
    }

    public List<AutoBid> getAutoBids() {
        return new java.util.ArrayList<>(autoBids);
    }

    public BidTransaction getWinner() {
        if (bidHistory.isEmpty())
            return null;
        return bidHistory.get(bidHistory.size() - 1);
    }

    public String getStatusDisplay() {
        return switch (status) {
            case UPCOMING, OPEN -> "Sắp diễn ra";
            case RUNNING -> "Đang diễn ra";
            case CLOSED -> "Đã đóng";
            case CANCELED -> "Đã huỷ";
            default -> status.name();
        };
    }
}
