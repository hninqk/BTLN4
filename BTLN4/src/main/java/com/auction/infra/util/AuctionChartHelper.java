package com.auction.infra.util;

import com.auction.core.model.BidTransaction;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the configuration and update logic for the real-time bid chart.
 * Implements data-clustering tooltip logic (collapse overlapping nodes).
 */
public class AuctionChartHelper {

    private final XYChart.Series<Number, Number> priceSeries;
    private final LineChart<Number, Number> priceChart;
    private final NumberAxis timeAxis;
    private DateTimeFormatter axisFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    
    private final long CLUSTER_THRESHOLD_MS = 60_000; // 1 minute clustering

    private static class ClusterData {
        long epochMillis;
        double maxPrice;
        String bidderName;
        XYChart.Data<Number, Number> dataNode;
        Tooltip tooltip;

        public ClusterData(long epochMillis, double maxPrice, String bidderName, XYChart.Data<Number, Number> dataNode) {
            this.epochMillis = epochMillis;
            this.maxPrice = maxPrice;
            this.bidderName = bidderName;
            this.dataNode = dataNode;
        }
        
        public void updateTooltip() {
            if (tooltip != null) {
                String timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                String text = "Thời gian: " + timeStr + "\n" +
                              "Giá cao nhất: " + String.format("%,.0f", maxPrice) + " đ\n" +
                              "Người đấu giá: " + bidderName;
                tooltip.setText(text);
            }
        }
    }
    
    private final List<ClusterData> clusters = new ArrayList<>();

    public AuctionChartHelper(LineChart<Number, Number> priceChart, NumberAxis timeAxis) {
        this.priceChart = priceChart;
        this.timeAxis = timeAxis;
        this.priceSeries = new XYChart.Series<>();
        setupChart();
    }

    private void setupChart() {
        priceSeries.setName("Giá đấu");
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(false);

        if (timeAxis != null) {
            timeAxis.setAutoRanging(true);
            timeAxis.setForceZeroInRange(false);
            timeAxis.setTickLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Number object) {
                    long epochMillis = object.longValue();
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("Asia/Ho_Chi_Minh"))
                            .format(axisFormatter);
                }
                @Override
                public Number fromString(String string) {
                    return 0;
                }
            });
        }
    }

    /** Adds a fully-formed BidTransaction to the chart. */
    public void addBid(BidTransaction bid) {
        addRawBid(bid.getAmount(), bid.getTimestamp(), bid.getBidder() != null ? bid.getBidder().getUsername() : "Ẩn danh");
    }

    /** Adds raw amount and timestamp to the chart, applying clustering logic with bidder name. */
    public void addRawBid(double amount, LocalDateTime ts, String bidderName) {
        long epochMillis = ts.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        
        if (!clusters.isEmpty()) {
            ClusterData lastCluster = clusters.get(clusters.size() - 1);
            if (Math.abs(epochMillis - lastCluster.epochMillis) <= CLUSTER_THRESHOLD_MS) {
                if (amount > lastCluster.maxPrice) {
                    lastCluster.maxPrice = amount;
                    lastCluster.bidderName = bidderName;
                    lastCluster.dataNode.setYValue(amount);
                }
                lastCluster.updateTooltip();
                return;
            }
        }
        
        XYChart.Data<Number, Number> data = new XYChart.Data<>(epochMillis, amount);
        ClusterData cluster = new ClusterData(epochMillis, amount, bidderName, data);
        clusters.add(cluster);
        
        data.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Tooltip tooltip = new Tooltip();
                cluster.tooltip = tooltip;
                cluster.updateTooltip();
                Tooltip.install(newNode, tooltip);
                
                // Add hover effect
                newNode.setOnMouseEntered(e -> newNode.setStyle("-fx-scale-x: 1.5; -fx-scale-y: 1.5; -fx-cursor: hand;"));
                newNode.setOnMouseExited(e -> newNode.setStyle("-fx-scale-x: 1.0; -fx-scale-y: 1.0;"));
            }
        });
        
        priceSeries.getData().add(data);
    }

    public void setTimeWindow(LocalDateTime start, LocalDateTime end, String format, long tickMillis) {
        if (timeAxis == null) return;
        axisFormatter = DateTimeFormatter.ofPattern(format);
        timeAxis.setAutoRanging(true);
    }

    /** Clears all data from the chart. */
    public void clear() {
        priceSeries.getData().clear();
        clusters.clear();
    }
}
