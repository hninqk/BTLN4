package com.auction.core.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import java.util.function.UnaryOperator;

public class CurrencyUtil {

    public static void setupCurrencyTextField(TextField textField) {
        if (textField == null) return;

        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (!change.isContentChange()) {
                return change;
            }

            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change;
            }

            String cleanString = newText.replaceAll("[^\\d]", "");
            if (cleanString.isEmpty()) {
                return null;
            }

            try {
                long parsed = Long.parseLong(cleanString);
                String formatted = String.format("%,d", parsed);

                int digitsBeforeCaret = 0;
                int proposedCaret = change.getCaretPosition();
                for (int i = 0; i < proposedCaret && i < newText.length(); i++) {
                    if (Character.isDigit(newText.charAt(i))) {
                        digitsBeforeCaret++;
                    }
                }

                change.setRange(0, change.getControlText().length());
                change.setText(formatted);

                int finalCaret = formatted.length();
                int digitsSeen = 0;
                boolean caretPlaced = false;
                for (int i = 0; i < formatted.length(); i++) {
                    if (digitsSeen == digitsBeforeCaret) {

                        finalCaret = i;
                        caretPlaced = true;
                        break;
                    }
                    if (Character.isDigit(formatted.charAt(i))) {
                        digitsSeen++;
                    }
                }

                change.setCaretPosition(finalCaret);
                change.setAnchor(finalCaret);

                return change;

            } catch (NumberFormatException e) {
                return null;
            }
        };

        textField.setTextFormatter(new TextFormatter<>(filter));
    }

    public static double parseCurrency(String formattedValue) throws NumberFormatException {
        if (formattedValue == null || formattedValue.trim().isEmpty()) {
            return 0;
        }
        String cleanString = formattedValue.replaceAll("[^\\d]", "");
        return Double.parseDouble(cleanString);
    }
}
