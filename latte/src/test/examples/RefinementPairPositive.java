package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int pairCount")
// @StateSet({"unpaired", "paired"})
public class RefinementPairPositive {
    @Unique CellPair left;
    @Unique CellPair right;

    // both cells carry initialized values and remain semantically distinct.
    // @Refinement("a.value != null && b.value != null && a.value != b.value && this.left == a && this.right == b && this.left.value != this.right.value")
    // @StateRefinement(from="unpaired(this)", to="paired(this) && pairCount(this) == pairCount(old(this)) + 1")
    void setDistinct(@Free CellPair a, @Free CellPair b) {
        this.left = a;
        this.right = b;
    }

    public static void main(String[] args) {
        CellPair a = new CellPair(new Object());
        CellPair b = new CellPair(new Object());
        RefinementPairPositive test = new RefinementPairPositive();
        test.setDistinct(a, b);
    }
}

class CellPair {
    @Unique Object value;

    CellPair(@Free Object value) {
        this.value = value;
    }
}
