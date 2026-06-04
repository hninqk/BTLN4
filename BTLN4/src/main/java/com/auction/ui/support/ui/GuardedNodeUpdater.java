package com.auction.ui.support.ui;

import javafx.scene.Node;
import javafx.scene.control.Labeled;
import java.util.Objects;

public interface GuardedNodeUpdater {
    void setTextIfChanged(Labeled node, String newText);

    void setVisibleIfChanged(Node node, boolean visible);

    void setManagedIfChanged(Node node, boolean managed);

    void setDisableIfChanged(Node node, boolean disable);

    final class Default implements GuardedNodeUpdater {
        @Override
        public void setTextIfChanged(Labeled node, String newText) {
            if (node != null && !Objects.equals(node.getText(), newText)) {
                node.setText(newText);
            }
        }

        @Override
        public void setVisibleIfChanged(Node node, boolean visible) {
            if (node != null && node.isVisible() != visible) {
                node.setVisible(visible);
            }
        }

        @Override
        public void setManagedIfChanged(Node node, boolean managed) {
            if (node != null && node.isManaged() != managed) {
                node.setManaged(managed);
            }
        }

        @Override
        public void setDisableIfChanged(Node node, boolean disable) {
            if (node != null && node.isDisable() != disable) {
                node.setDisable(disable);
            }
        }
    }
}
