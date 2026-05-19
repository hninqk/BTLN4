package com.auction.model;

import java.time.LocalDateTime;

/**
 * Bidder – người tham gia đấu giá.
 *
 * Cơ chế Đóng băng Số dư (Fund Freezing):
 *   totalBalance    = Tổng số dư tài khoản (accountBalance)
 *   frozenBalance   = Số tiền đang bị đóng băng (đang hold cho 1 bid đang dẫn đầu)
 *   availableBalance = totalBalance - frozenBalance (số dư có thể dùng để bid)
 *
 * Khi đặt giá: freeze(amount) → frozenBalance += amount
 * Khi bị outbid: unfreeze(amount) → frozenBalance -= amount
 * Khi thắng:  deductBalance(amount) → totalBalance -= amount, frozenBalance -= amount
 */
public class Bidder extends User {

    /** Tổng số dư tài khoản (= tiền thực sự có). */
    private double accountBalance;

    /**
     * Số tiền đang bị đóng băng – đang được hold cho 1 phiên đấu giá mà
     * bidder này đang dẫn đầu. Không thể dùng để bid ở phiên khác.
     */
    private double frozenBalance;

    /** Normal constructor – frozenBalance mặc định = 0 */
    public Bidder(String username, String password, double accountBalance) {
        super(username, password);
        this.accountBalance = accountBalance;
        this.frozenBalance  = 0.0;
    }

    /** DB reconstruction constructor (không có frozenBalance – backward compat) */
    public Bidder(String id, LocalDateTime createdAt, String username, String password, double accountBalance) {
        super(id, createdAt, username, password);
        this.accountBalance = accountBalance;
        this.frozenBalance  = 0.0;
    }

    /** DB reconstruction constructor với frozenBalance */
    public Bidder(String id, LocalDateTime createdAt, String username, String password,
                  double accountBalance, double frozenBalance) {
        super(id, createdAt, username, password);
        this.accountBalance = accountBalance;
        this.frozenBalance  = Math.max(0.0, frozenBalance);
    }

    @Override
    public String getRole() { return "Bidder"; }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** Tổng số dư tài khoản. */
    public double getAccountBalance() { return accountBalance; }

    /** Số tiền đang bị đóng băng (đang hold cho bid dẫn đầu). */
    public double getFrozenBalance() { return frozenBalance; }

    /**
     * Số dư khả dụng = totalBalance - frozenBalance.
     * Đây là con số phải dùng để kiểm tra khi bidder muốn đặt giá mới.
     */
    public double getAvailableBalance() {
        return Math.max(0.0, accountBalance - frozenBalance);
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setAccountBalance(double accountBalance) { this.accountBalance = accountBalance; }

    public void setFrozenBalance(double frozenBalance) {
        this.frozenBalance = Math.max(0.0, frozenBalance);
    }

    // ── Balance mutations ─────────────────────────────────────────────────────

    public void AddBalance(double amount) {
        if (amount > 0) accountBalance += amount;
    }

    /** alias kept for service layer */
    public void addBalance(double amount) { AddBalance(amount); }

    /**
     * Đóng băng một khoản tiền khi bidder đặt giá thành công.
     * Kiểm tra availableBalance trước khi gọi method này.
     *
     * @param amount số tiền cần đóng băng (> 0)
     * @return true nếu đủ available balance để freeze
     */
    public synchronized boolean freezeFunds(double amount) {
        if (amount <= 0) return false;
        if (getAvailableBalance() < amount) return false;
        this.frozenBalance += amount;
        return true;
    }

    /**
     * Hoàn trả số tiền đóng băng khi bidder bị outbid.
     *
     * @param amount số tiền cần hoàn trả
     */
    public synchronized void unfreezeFunds(double amount) {
        if (amount <= 0) return;
        this.frozenBalance = Math.max(0.0, this.frozenBalance - amount);
    }

    /**
     * Trừ tiền khi bidder thắng đấu giá.
     * Đồng thời giảm frozenBalance tương ứng (tiền đã được freeze trước đó).
     *
     * @param amount số tiền thắng cuộc cần trừ
     * @return true nếu trừ thành công
     */
    public synchronized boolean deductBalance(double amount) {
        if (amount > 0 && this.accountBalance >= amount) {
            this.accountBalance -= amount;
            // Giảm frozen balance tương ứng (tiền đã được freeze từ trước)
            this.frozenBalance = Math.max(0.0, this.frozenBalance - amount);
            return true;
        }
        return false;
    }
}
