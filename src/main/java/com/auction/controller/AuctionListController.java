package com.auction.controller;

import javafx.animation.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.*;

public class AuctionListController {
    @FXML private StackPane rootPane;
    @FXML private BorderPane mainPane;
    @FXML private HBox topBar, bidRow;
    @FXML private Label titleLabel, walletLabel, listTitle;
    @FXML private Label itemName, itemDesc, currentLbl, currentPrice, timeLbl, timeLeft, bidsLbl, bidCount;
    @FXML private VBox leftPanel, centerPane, detailCard, itemListBox;
    @FXML private ScrollPane itemScroll;
    @FXML private LineChart<Number, Number> priceChart;
    @FXML private NumberAxis xAxis, yAxis;
    @FXML private TextField bidField;
    @FXML private Button bidButton;
    @FXML private StackPane chartContainer;
    @FXML private Label toast;

    private final List<AuctionItem> items = new ArrayList<>();
    private AuctionItem selected;
    private VBox selectedCard;
    private double userBalance = 25000.0;

    // Palette (cyber-luxe)
    private static final String BG_DEEP   = "#0b0f1a";
    private static final String ACCENT    = "#00e5ff";
    private static final String ACCENT2   = "#ff3df2";
    private static final String GOLD      = "#ffb84d";
    private static final String TXT_MAIN  = "#e8ecff";
    private static final String TXT_DIM   = "#8a94b8";
    private static final String BG_CARD   = "#1b2238";

    @FXML
    public void initialize() {
        styleRoot();
        buildSampleItems();
        populateItemList();
        styleChart();
        styleBidRow();
        setupToast();

        // Select first item by default if available
        if (!items.isEmpty()) {
            // Need to run after UI is rendered to get the card reference
            javafx.application.Platform.runLater(() -> {
                VBox firstCard = (VBox) itemListBox.getChildren().get(0);
                selectItem(items.get(0), firstCard);
            });
        }
    }

    /* ---------------- STYLING ---------------- */
    private void styleRoot() {
        rootPane.setStyle("-fx-background-color: linear-gradient(to bottom right, " + BG_DEEP + ", #1a1033);");
        topBar.setStyle("-fx-background-color: linear-gradient(to right, rgba(0,229,255,0.08), rgba(255,61,242,0.08));" +
                "-fx-border-color: transparent transparent " + ACCENT + " transparent;" +
                "-fx-border-width: 0 0 1 0;");

        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web(TXT_MAIN));
        titleLabel.setEffect(new Glow(0.6));

        walletLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        walletLabel.setTextFill(Color.web(GOLD));
        walletLabel.setStyle("-fx-background-color: rgba(255,184,77,0.12); -fx-background-radius: 20; -fx-padding: 6 16; -fx-border-color: " + GOLD + "; -fx-border-radius: 20;");

        leftPanel.setStyle("-fx-background-color: rgba(20,26,42,0.7); -fx-border-color: transparent " + ACCENT + " transparent transparent; -fx-border-width: 0 1 0 0;");
        itemScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        detailCard.setStyle("-fx-background-color: linear-gradient(to bottom right, " + BG_CARD + ", #221a3d); -fx-background-radius: 18; -fx-border-color: linear-gradient(to right, " + ACCENT + ", " + ACCENT2 + "); -fx-border-radius: 18; -fx-border-width: 1.5;");
        styleMiniLabel(currentLbl); styleBigValue(currentPrice, ACCENT);
        styleMiniLabel(timeLbl);    styleBigValue(timeLeft, GOLD);
        styleMiniLabel(bidsLbl);    styleBigValue(bidCount, ACCENT2);
    }

    private void styleMiniLabel(Label l) {
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(TXT_DIM));
    }

    private void styleBigValue(Label l, String color) {
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        l.setTextFill(Color.web(color));
    }

    private void styleChart() {
        xAxis.setTickLabelFill(Color.web(TXT_DIM));
        yAxis.setTickLabelFill(Color.web(TXT_DIM));
        priceChart.setLegendVisible(false);
        priceChart.setAnimated(false); // Animated true can cause issues with dynamic updates

        // Use lookup after scene is showing to hide plot background properly
        priceChart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Node plot = priceChart.lookup(".chart-plot-background");
                if (plot != null) plot.setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private void styleBidRow() {
        bidField.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: white; -fx-background-radius: 30; -fx-border-color: " + ACCENT + "; -fx-border-radius: 30;");
        bidButton.setStyle("-fx-background-color: linear-gradient(to right, " + ACCENT + ", " + ACCENT2 + "); -fx-text-fill: #0b0f1a; -fx-font-weight: bold; -fx-background-radius: 30; -fx-cursor: hand;");
    }

    private void setupToast() {
        toast.setOpacity(0);
        toast.setMouseTransparent(true);
    }

    /* ---------------- DATA + LOGIC ---------------- */
    private void buildSampleItems() {
        items.add(new AuctionItem("Neon Dragon", "Cyberpunk sculpture.", 2400, "02:14:33", 47, genTrend(2400)));
        items.add(new AuctionItem("Vintage Rolex", "1962 collector's piece.", 18500, "01:03:12", 89, genTrend(18500)));
    }

    private List<double[]> genTrend(double current) {
        List<double[]> trend = new ArrayList<>();
        Random r = new Random();
        for (int i = 0; i < 10; i++) {
            trend.add(new double[]{i, current - (r.nextDouble() * 500)});
        }
        return trend;
    }

    private void populateItemList() {
        itemListBox.getChildren().clear();
        for (AuctionItem it : items) {
            VBox card = makeItemCard(it);
            itemListBox.getChildren().add(card);
        }
    }

    private VBox makeItemCard(AuctionItem it) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setCursor(Cursor.HAND);
        setCardStyle(card, false);

        Label name = new Label(it.name);
        name.setTextFill(Color.web(TXT_MAIN));
        name.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label price = new Label("$" + it.currentBid);
        price.setTextFill(Color.web(ACCENT));

        card.getChildren().addAll(name, price);
        card.setOnMouseClicked(e -> selectItem(it, card));
        return card;
    }

    private void selectItem(AuctionItem it, VBox card) {
        if (selectedCard != null) setCardStyle(selectedCard, false);
        selectedCard = card;
        selected = it;
        setCardStyle(card, true);

        itemName.setText(it.name);
        itemDesc.setText(it.description);
        currentPrice.setText("$" + String.format("%,.2f", it.currentBid));
        timeLeft.setText(it.timeLeft);
        bidCount.setText(String.valueOf(it.bidCount));

        updateChart(it);
    }

    private void updateChart(AuctionItem it) {
        priceChart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (double[] pt : it.history) {
            series.getData().add(new XYChart.Data<>(pt[0], pt[1]));
        }
        priceChart.getData().add(series);
    }

    @FXML
    private void onBid(ActionEvent event) {
        try {
            double amount = Double.parseDouble(bidField.getText());
            if (amount > selected.currentBid && userBalance >= amount) {
                selected.currentBid = amount;
                selected.bidCount++;
                showToast("BID PLACED SUCCESSFULLY!");
                selectItem(selected, selectedCard); // Refresh view
                bidField.clear();
            } else {
                showToast("INVALID BID AMOUNT");
            }
        } catch (Exception e) {
            showToast("ENTER A VALID NUMBER");
        }
    }

    private void showToast(String msg) {
        toast.setText(msg);
        FadeTransition ft = new FadeTransition(Duration.millis(300), toast);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(e2 -> {
                FadeTransition out = new FadeTransition(Duration.millis(300), toast);
                out.setFromValue(1); out.setToValue(0);
                out.play();
            });
            pause.play();
        });
        ft.play();
    }

    private void setCardStyle(VBox card, boolean selected) {
        if (selected) {
            card.setStyle("-fx-background-color: rgba(0, 229, 255, 0.2); -fx-border-color: " + ACCENT + "; -fx-border-width: 2; -fx-background-radius: 10; -fx-border-radius: 10;");
        } else {
            card.setStyle("-fx-background-color: " + BG_CARD + "; -fx-border-color: rgba(255,255,255,0.1); -fx-border-width: 1; -fx-background-radius: 10; -fx-border-radius: 10;");
        }
    }

    // Helper class
    public static class AuctionItem {
        String name, description, timeLeft;
        double currentBid;
        int bidCount;
        List<double[]> history;

        public AuctionItem(String name, String description, double currentBid, String timeLeft, int bidCount, List<double[]> history) {
            this.name = name; this.description = description; this.currentBid = currentBid;
            this.timeLeft = timeLeft; this.bidCount = bidCount; this.history = history;
        }
    }
}