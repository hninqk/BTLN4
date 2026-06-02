package com.auction.ui.support.logic;

import com.auction.ui.support.dto.FeedEntry;

public interface LiveFeedEntryParser {
    FeedEntry parse(String entry);

    final class Default implements LiveFeedEntryParser {
        @Override
        public FeedEntry parse(String entry) {
            String time = "";
            String body = entry;
            int close = entry.indexOf(']');
            if (entry.startsWith("[") && close > 1) {
                time = entry.substring(1, close);
                body = entry.substring(close + 1).trim();
            }

            if (body.startsWith("Auto-Bid:")) {
                return new FeedEntry(time, "Auto-Bid", body.substring("Auto-Bid:".length()).trim(), "");
            }

            String[] parts = body.split("→", 2);
            if (parts.length == 2) {
                return new FeedEntry(time, parts[0].trim(), "Đặt giá mới", parts[1].trim());
            }

            return new FeedEntry(time, "Hệ thống", body, "");
        }
    }
}
