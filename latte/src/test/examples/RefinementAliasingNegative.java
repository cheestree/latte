package latte;

import specification.Free;
import specification.Shared;
import specification.Unique;

public class RefinementAliasingNegative {
    @Unique CellAliasing left;
    @Unique CellAliasing right;

    // the two stored values should differ whenever both exist.
    // @Refinement("this.left == null || this.right == null || this.left.value != this.right.value")
    void breakAliasInvariant(@Shared CellAliasing c) {
        // assignment fails first at permission level (@Shared -> @Unique).
        this.left = c;
        this.right = c;
    }

    public static void main(String[] args) {
        CellAliasing cell = new CellAliasing(new Object());
        RefinementAliasingNegative test = new RefinementAliasingNegative();
        test.breakAliasInvariant(cell); // Should fail permission check
    }
}

class CellAliasing {
    @Unique Object value;

    CellAliasing(@Free Object value) {
        this.value = value;
    }
}
