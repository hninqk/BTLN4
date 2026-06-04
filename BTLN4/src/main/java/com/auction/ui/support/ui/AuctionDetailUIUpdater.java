package com.auction.ui.support.ui;

import com.auction.core.util.BidLadderUtil;
import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.BidTransaction;
import com.auction.core.model.Bidder;
import com.auction.core.model.Seller;
import com.auction.core.model.User;
import com.auction.core.util.SessionManager;
import com.auction.core.util.TimeSyncManager;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuctionDetailUIUpdater {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final GuardedNodeUpdater NODE_UPDATER = new GuardedNodeUpdater.Default();

    private final Label timeRemainingLabel;

    private final Label timeLabelPrefix;

    private final Label currentPriceLabel;

    private final Label bidCountLabel;

    private final Label minBidHint;

    private final Label stepHintLabel;

    private final Label balanceLabel;

    private final Label frozenLabel;

    private final Label statusBadge;

    private final Label startTimeLabel;

    private final VBox winnerBox;

    private final Label winnerLabel;

    private final Label winnerPriceLabel;

    private String currentStatusBadgeClass = "";

    private final Label bidErrorLabel;

    private final Button placeBidButton;

    private final TextField bidAmountField;

    private final Label sellerWarningLabel;

    private final Button autoBidToggleButton;

    private final VBox autoBidPopup;

    private final Button registerAutoBidButton;

    private final TextField autoMaxBidField;

    private final Label autoBidErrorLabel;

    public AuctionDetailUIUpdater(
            Label timeRemainingLabel,
            Label timeLabelPrefix,
            Label currentPriceLabel,
            Label bidCountLabel,
            Label minBidHint,
            Label stepHintLabel,
            Label balanceLabel,
            Label frozenLabel,
            Label statusBadge,
            Label startTimeLabel,
            VBox winnerBox,
            Label winnerLabel,
            Label winnerPriceLabel,
            Label bidErrorLabel,
            Button placeBidButton,
            TextField bidAmountField,
            Label sellerWarningLabel,
            Button autoBidToggleButton,
            VBox autoBidPopup,
            Button registerAutoBidButton,
            TextField autoMaxBidField,
            Label autoBidErrorLabel) {
        this.timeRemainingLabel = timeRemainingLabel;
        this.timeLabelPrefix = timeLabelPrefix;
        this.currentPriceLabel = currentPriceLabel;
        this.bidCountLabel = bidCountLabel;
        this.minBidHint = minBidHint;
        this.stepHintLabel = stepHintLabel;
        this.balanceLabel = balanceLabel;
        this.frozenLabel = frozenLabel;
        this.statusBadge = statusBadge;
        this.startTimeLabel = startTimeLabel;
        this.winnerBox = winnerBox;
        this.winnerLabel = winnerLabel;
        this.winnerPriceLabel = winnerPriceLabel;
        this.bidErrorLabel = bidErrorLabel;
        this.placeBidButton = placeBidButton;
        this.bidAmountField = bidAmountField;
        this.sellerWarningLabel = sellerWarningLabel;
        this.autoBidToggleButton = autoBidToggleButton;
        this.autoBidPopup = autoBidPopup;
        this.registerAutoBidButton = registerAutoBidButton;
        this.autoMaxBidField = autoMaxBidField;
        this.autoBidErrorLabel = autoBidErrorLabel;
    }

    public void refreshControls(Auction currentAuction, boolean wsConnected, boolean isHighestBidder) {
        if (currentAuction == null) return;

        AuctionStatus status = currentAuction.getStatus();
        User user = SessionManager.getInstance().getCurrentUser();
        boolean isExpired = currentAuction.getEndTime() != null
                && TimeSyncManager.getNow().isAfter(currentAuction.getEndTime());
        boolean canBid = status == AuctionStatus.RUNNING
                && !isExpired
                && user instanceof Bidder
                && wsConnected
                && !isHighestBidder;

        if (sellerWarningLabel != null) {
            boolean isSeller = user instanceof Seller;
            setVisibleIfChanged(sellerWarningLabel, isSeller);
            setManagedIfChanged(sellerWarningLabel, isSeller);
        }

        boolean canAutoBid = status == AuctionStatus.RUNNING && !isExpired && user instanceof Bidder && wsConnected;

        setDisableIfChanged(placeBidButton, !canBid);
        setDisableIfChanged(bidAmountField, !canBid);

        if (autoBidToggleButton != null) {
            setDisableIfChanged(autoBidToggleButton, !canAutoBid);
            setVisibleIfChanged(autoBidToggleButton, true);
            setManagedIfChanged(autoBidToggleButton, true);
            if (!canAutoBid && autoBidPopup != null && autoBidPopup.isVisible()) {
                autoBidPopup.setVisible(false);
                autoBidPopup.setManaged(false);
            }
        }

        if (registerAutoBidButton != null) {
            setDisableIfChanged(registerAutoBidButton, !canAutoBid);
            setDisableIfChanged(autoMaxBidField, !canAutoBid);

            if (autoBidErrorLabel != null) {
                String abCur = autoBidErrorLabel.getText();
                if (abCur.contains("Đang gửi")) {
                    setTextIfChanged(autoBidErrorLabel, "");
                    autoBidErrorLabel.setStyle("");
                }
            }
        }

        if (isHighestBidder) {
            setTextIfChanged(bidErrorLabel, "Bạn đang là người ra giá cao nhất.");
            bidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
            return;
        }

        String cur = bidErrorLabel.getText();
        if (cur.contains("Đang gửi") || cur.contains("người ra giá cao nhất")) {
            setTextIfChanged(bidErrorLabel, "");
            bidErrorLabel.setStyle("");
        }
    }

    private void setDisableIfChanged(Node node, boolean disable) {
        NODE_UPDATER.setDisableIfChanged(node, disable);
    }

    public void refreshCountdown(Auction currentAuction) {
        if (currentAuction == null) return;

        AuctionStatus status = currentAuction.getStatus();
        String countdownText = switch (status) {
            case UPCOMING, OPEN -> {
                LocalDateTime start = currentAuction.getStartTime();
                if (start == null) yield "Sắp diễn ra";
                Duration remaining = Duration.between(TimeSyncManager.getNow(), start);
                yield remaining.isNegative() || remaining.isZero()
                        ? "Đang chuẩn bị bắt đầu"
                        : String.format("Bắt đầu sau %02d:%02d:%02d",
                                remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart());
            }
            case CLOSED, CANCELED -> "Đã kết thúc";
            case RUNNING -> {
                if (currentAuction.getEndTime() == null) yield "Hết giờ";
                Duration remaining = Duration.between(TimeSyncManager.getNow(), currentAuction.getEndTime());
                yield remaining.isNegative() ? "Hết giờ"
                        : String.format("%02d:%02d:%02d",
                                remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart());
            }
            default -> "";
        };
        setTextIfChanged(timeRemainingLabel, countdownText);
    }

    public void refreshBidSection(Auction currentAuction) {
        if (currentAuction == null) return;

        setTextIfChanged(currentPriceLabel, String.format("%,.0f ₫", currentAuction.getHighestBid()));
        setTextIfChanged(bidCountLabel, currentAuction.getBidHistory().size() + " lượt đấu giá");
        double step = com.auction.core.util.BidLadderUtil.getIncrementForPrice(currentAuction.getHighestBid());
        setTextIfChanged(minBidHint, String.format("%,.0f ₫", currentAuction.getHighestBid() + step));
        if (stepHintLabel != null) {
            setTextIfChanged(stepHintLabel, String.format("%,.0f ₫", step));
        }
    }

    public void refreshBalanceSection() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder) {
            double available = bidder.getAvailableBalance();
            double frozen = bidder.getFrozenBalance();
            setTextIfChanged(balanceLabel, String.format("%,.0f ₫", available));
            setVisibleIfChanged(balanceLabel, true);

            if (frozenLabel != null) {
                if (frozen > 0) {
                    setTextIfChanged(frozenLabel, String.format("%,.0f ₫", frozen));
                    setVisibleIfChanged(frozenLabel, true);
                    setManagedIfChanged(frozenLabel, true);
                } else {
                    setVisibleIfChanged(frozenLabel, false);
                    setManagedIfChanged(frozenLabel, false);
                }
            }
        } else {
            setVisibleIfChanged(balanceLabel, false);
            if (frozenLabel != null) {
                setVisibleIfChanged(frozenLabel, false);
                setManagedIfChanged(frozenLabel, false);
            }
        }
    }

    public void refreshStatusSection(Auction currentAuction) {
        if (currentAuction == null) return;
        updateStatusBadge(currentAuction);
        LocalDateTime st = currentAuction.getStartTime();
        setTextIfChanged(startTimeLabel, st != null ? st.format(FMT) : "Chưa bắt đầu");
    }

    public void refreshWinnerSection(Auction currentAuction) {
        if (currentAuction == null) return;

        AuctionStatus status = currentAuction.getStatus();
        if (status == AuctionStatus.CLOSED) {
            BidTransaction winner = currentAuction.getWinner();
            if (winner != null) {
                setVisibleIfChanged(winnerBox, true);
                setManagedIfChanged(winnerBox, true);
                setTextIfChanged(winnerLabel, "Người thắng: " + winner.getBidder().getUsername());
                setTextIfChanged(winnerPriceLabel, String.format("Giá chốt: %,.0f ₫", winner.getAmount()));
            }
        } else {
            setVisibleIfChanged(winnerBox, false);
            setManagedIfChanged(winnerBox, false);
        }
    }

    private void updateStatusBadge(Auction currentAuction) {
        AuctionStatus status = currentAuction.getStatus();
        setTextIfChanged(statusBadge, currentAuction.getStatusDisplay());
        String cls = switch (status) {
            case UPCOMING, OPEN -> "badge-open";
            case RUNNING -> "badge-running";
            case CLOSED -> "badge-closed";
            case CANCELED -> "badge-canceled";
            default -> "badge-open";
        };
        if (!cls.equals(currentStatusBadgeClass)) {
            statusBadge.getStyleClass().removeAll(
                    "badge-pending", "badge-open", "badge-running", "badge-closed", "badge-canceled");
            statusBadge.getStyleClass().add(cls);
            currentStatusBadgeClass = cls;
        }
    }

    private void setTextIfChanged(Labeled node, String newText) {
        NODE_UPDATER.setTextIfChanged(node, newText);
    }

    private void setVisibleIfChanged(Node node, boolean visible) {
        NODE_UPDATER.setVisibleIfChanged(node, visible);
    }

    private void setManagedIfChanged(Node node, boolean managed) {
        NODE_UPDATER.setManagedIfChanged(node, managed);
    }
}
