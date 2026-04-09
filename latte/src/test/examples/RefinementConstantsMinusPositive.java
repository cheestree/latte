package latte;

import specification.Free;
import specification.Unique;

public class RefinementConstantsMinusPositive {

    @Unique RangeCell current;

    public RefinementConstantsMinusPositive(@Free RangeCell current) {
        this.current = current;
    }

    // @Refinement("limit > 10 && (_ == null || _.value < limit - 2)")
    @Free RangeCell detachIfSmall(int limit) {
        if (this.current == null) {
            return null;
        }
        RangeCell out = this.current;
        this.current = null;
        return out;
    }
}

class RangeCell {
    int value;
    @Unique RangeCell next;

    RangeCell(int value, @Free RangeCell next) {
        this.value = value;
        this.next = next;
    }
}
