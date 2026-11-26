package app.glassworks.extension.tidal.pill;

import androidx.compose.ui.node.ModifierNodeElement;

/**
 * Stateless element that creates a {@link FrostedPillNode}.
 * All instances are equal, so Compose reuses the node across recompositions.
 */
final class FrostedPillElement extends ModifierNodeElement<FrostedPillNode> {

    static final FrostedPillElement INSTANCE = new FrostedPillElement();

    private FrostedPillElement() {
    }

    @Override
    public FrostedPillNode create() {
        return new FrostedPillNode();
    }

    @Override
    public void update(FrostedPillNode node) {
        // Nothing to update, the element has no parameters.
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FrostedPillElement;
    }

    @Override
    public int hashCode() {
        return 0x7D_61A5;
    }
}
