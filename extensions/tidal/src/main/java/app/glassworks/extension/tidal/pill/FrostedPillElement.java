package app.glassworks.extension.tidal.pill;

import androidx.compose.ui.node.ModifierNodeElement;

/**
 * Stateless element that creates a {@link FrostedPillNode} of one kind.
 * Elements of the same kind are equal, so Compose reuses the node across recompositions.
 */
final class FrostedPillElement extends ModifierNodeElement<FrostedPillNode> {

    /** The mini player pill. */
    static final int KIND_MINI_PLAYER = 0;
    /** The tab bar pill, which may be split to show one tab in its own round pill. */
    static final int KIND_TAB_BAR = 1;
    /** One pill holding both the mini player and the tab bar. */
    static final int KIND_CARD = 2;

    static final FrostedPillElement MINI_PLAYER = new FrostedPillElement(KIND_MINI_PLAYER);
    static final FrostedPillElement TAB_BAR = new FrostedPillElement(KIND_TAB_BAR);
    static final FrostedPillElement CARD = new FrostedPillElement(KIND_CARD);

    private final int kind;

    private FrostedPillElement(int kind) {
        this.kind = kind;
    }

    @Override
    public FrostedPillNode create() {
        return new FrostedPillNode(kind);
    }

    @Override
    public void update(FrostedPillNode node) {
        // Nothing to update, the kind never changes for an element.
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FrostedPillElement && ((FrostedPillElement) other).kind == kind;
    }

    @Override
    public int hashCode() {
        return 0x7D_61A5 + kind;
    }
}
