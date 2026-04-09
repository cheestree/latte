package latte;

import specification.Free;
import specification.Unique;

public class RefinementPairPositive {
    @Unique Cell left;
    @Unique Cell right;

    // both cells carry initialized values and remain semantically distinct.
    // @Refinement("a.value != null && b.value != null && this.left.value != this.right.value")
    void setDistinct(@Free Cell a, @Free Cell b) {
        this.left = a;
        this.right = b;
    }
}

class Cell {
    @Unique Object value;

    Cell(@Free Object value) {
        this.value = value;
    }
}
