package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int weight")
public class RefinementOldGreaterNegative {

    @Unique LinkCell root;

    public RefinementOldGreaterNegative(@Free LinkCell root) {
        this.root = root;
    }

    // @StateRefinement(to="weight(this) > weight(old(this)) + 1 && return != null")
    @Free LinkCell takeFree(@Free LinkCell candidate) {
        return candidate;
    }

    void violatesOldStylePrecondition() {
        // Fails on permission checking before any value-level refinement can be checked.
        LinkCell out = takeFree(this.root);
        this.root = out;
    }
}

class LinkCell {
    int weight;
    @Unique LinkCell next;

    LinkCell(int weight, @Free LinkCell next) {
        this.weight = weight;
        this.next = next;
    }
}
