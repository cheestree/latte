package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int detachCount")
// @StateSet({"holding", "detached"})
public class RefinementConstantsMinusPositive {

    @Unique RangeCellMinus current;

    public RefinementConstantsMinusPositive(@Free RangeCellMinus current) {
        this.current = current;
    }

    // @Refinement("limit > 10 && this.current == null && (_ == null || _.value < limit - 2)")
    // @StateRefinement(from="holding(this)", to="detached(this) && detachCount(this) == detachCount(old(this)) + 1")
    @Free RangeCellMinus detachIfSmall(int limit) {
        if (this.current == null) {
            return null;
        }
        RangeCellMinus out = this.current;
        this.current = null;
        return out;
    }

    public static void main(String[] args) {
        RangeCellMinus cell = new RangeCellMinus(5, null);
        RefinementConstantsMinusPositive test = new RefinementConstantsMinusPositive(cell);
        RangeCellMinus out = test.detachIfSmall(15);
        System.out.println(out);
    }
}

class RangeCellMinus {
    int value;
    @Unique RangeCellMinus next;

    RangeCellMinus(int value, @Free RangeCellMinus next) {
        this.value = value;
        this.next = next;
    }
}
