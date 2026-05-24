package com.auction.util;

import com.auction.model.BidTransaction;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Encapsulates the configuration and update logic for the real-time bid chart.
 */
public class AuctionChartHelper {

    private final XYChart.Series<Number, Number> priceSeries;
    private final LineChart<Number, Number> priceChart;
    private final NumberAxis timeAxis;
    private DateTimeFormatter axisFormatter = DateTimeFormatter.ofPattern("HH:mm");

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
        long epochMillis = bid.getTimestamp().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        priceSeries.getData().add(new XYChart.Data<>(epochMillis, bid.getAmount()));
    }

    /** Adds raw amount and timestamp to the chart. */
    public void addRawBid(double amount, LocalDateTime ts) {
        long epochMillis = ts.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        priceSeries.getData().add(new XYChart.Data<>(epochMillis, amount));
    }

    public void setTimeWindow(LocalDateTime start, LocalDateTime end, String format, long tickMillis) {
        if (timeAxis == null) {
            return;
        }
        axisFormatter = DateTimeFormatter.ofPattern(format);
        if (start == null || end == null) {
            timeAxis.setAutoRanging(true);
            return;
        }

        long lower = start.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        long upper = end.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        timeAxis.setAutoRanging(false);
        timeAxis.setForceZeroInRange(false);
        timeAxis.setLowerBound(lower);
        timeAxis.setUpperBound(Math.max(lower + tickMillis, upper));
        timeAxis.setTickUnit(tickMillis);
    }

    /** Clears all data from the chart. */
    public void clear() {
        priceSeries.getData().clear();
    }
}
