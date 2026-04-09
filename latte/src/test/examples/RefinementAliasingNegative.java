package latte;

import specification.Free;
import specification.Shared;
import specification.Unique;

public class RefinementAliasingNegative {
    @Unique Cell left;
    @Unique Cell right;

    // the two stored values should differ whenever both exist.
    // @Refinement("this.left == null || this.right == null || this.left.value != this.right.value")
    void breakAliasInvariant(@Shared Cell c) {
        // assignment fails first at permission level (@Shared -> @Unique).
        this.left = c;
        this.right = c;
    }
}

class Cell {
    @Unique Object value;

    Cell(@Free Object value) {
        this.value = value;
    }
}
