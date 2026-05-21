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
                private final DateTimeFormatter axisFmt = DateTimeFormatter.ofPattern("HH:mm");
                @Override
                public String toString(Number object) {
                    long epochMillis = object.longValue();
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(axisFmt);
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
        long epochMillis = bid.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        priceSeries.getData().add(new XYChart.Data<>(epochMillis, bid.getAmount()));
    }

    /** Adds raw amount and timestamp to the chart. */
    public void addRawBid(double amount, LocalDateTime ts) {
        long epochMillis = ts.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        priceSeries.getData().add(new XYChart.Data<>(epochMillis, amount));
    }

    /** Clears all data from the chart. */
    public void clear() {
        priceSeries.getData().clear();
    }
}
