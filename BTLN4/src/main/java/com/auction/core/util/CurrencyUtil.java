package com.auction.core.util;
import com.auction.ui.util.*;
import com.auction.core.util.*;
import com.auction.api.config.*;
import com.auction.infra.db.*;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import java.util.function.UnaryOperator;

public class CurrencyUtil {

    /**
     * Attaches a TextFormatter to the text field to format it with thousands separators 
     * as the user types. Prevents non-numeric input and maintains the correct cursor position atomically.
     */
    public static void setupCurrencyTextField(TextField textField) {
        if (textField == null) return;
        
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (!change.isContentChange()) {
                return change; // Allow selection changes, caret movement
            }

            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change; // Allow clearing the text field
            }

            // Strip all non-digits
            String cleanString = newText.replaceAll("[^\\d]", "");
            if (cleanString.isEmpty()) {
                return null; // Reject change if no digits remain
            }

            try {
                long parsed = Long.parseLong(cleanString);
                String formatted = String.format("%,d", parsed);

                // Calculate how many digits are BEFORE the proposed caret position in the raw new string
                int digitsBeforeCaret = 0;
                int proposedCaret = change.getCaretPosition();
                for (int i = 0; i < proposedCaret && i < newText.length(); i++) {
                    if (Character.isDigit(newText.charAt(i))) {
                        digitsBeforeCaret++;
                    }
                }

                // Modify the change to replace the ENTIRE text with the formatted string
                change.setRange(0, change.getControlText().length());
                change.setText(formatted);

                // Find the new caret position in the formatted string that matches the same number of digits.
                // Walk through the formatted string, counting digits; stop when we've counted
                // as many digits as were before the caret in the raw (pre-format) new text.
                int finalCaret = formatted.length(); // default: end of string
                int digitsSeen = 0;
                boolean caretPlaced = false;
                for (int i = 0; i < formatted.length(); i++) {
                    if (digitsSeen == digitsBeforeCaret) {
                        // We have counted exactly the right number of digits – place the
                        // caret here (before any trailing comma/separator at position i).
                        finalCaret = i;
                        caretPlaced = true;
                        break;
                    }
                    if (Character.isDigit(formatted.charAt(i))) {
                        digitsSeen++;
                    }
                }
                // If the loop ended without placing the caret (all digits consumed),
                // finalCaret stays at formatted.length() which is the correct end position.

                change.setCaretPosition(finalCaret);
                change.setAnchor(finalCaret);

                return change;

            } catch (NumberFormatException e) {
                return null; // Reject change if the number exceeds Long.MAX_VALUE
            }
        };

        textField.setTextFormatter(new TextFormatter<>(filter));
    }

    /**
     * Parses a formatted currency string into a double, removing commas.
     */
    public static double parseCurrency(String formattedValue) throws NumberFormatException {
        if (formattedValue == null || formattedValue.trim().isEmpty()) {
            return 0;
        }
        String cleanString = formattedValue.replaceAll("[^\\d]", "");
        return Double.parseDouble(cleanString);
    }
}
