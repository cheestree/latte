package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int weight")
// @StateSet({"light", "heavy"})
public class RefinementOldGreaterNegative {

    @Unique LinkCellOldGreater root;

    public RefinementOldGreaterNegative(@Free LinkCellOldGreater root) {
        this.root = root;
    }

    // @StateRefinement(from="light(this) && candidate != null", to="heavy(this) && weight(this) > weight(old(this)) + 1 && return != null")
    @Free LinkCellOldGreater takeFree(@Free LinkCellOldGreater candidate) {
        return candidate;
    }

    void violatesOldStylePrecondition() {
        // Fails on permission checking before any value-level refinement can be checked.
        LinkCellOldGreater out = takeFree(this.root);
        this.root = out;
    }

    public static void main(String[] args) {
        LinkCellOldGreater cell = new LinkCellOldGreater(1, null);
        RefinementOldGreaterNegative test = new RefinementOldGreaterNegative(cell);
        test.violatesOldStylePrecondition();
    }
}

class LinkCellOldGreater {
    int weight;
    @Unique LinkCellOldGreater next;

    LinkCellOldGreater(int weight, @Free LinkCellOldGreater next) {
        this.weight = weight;
        this.next = next;
    }
}
