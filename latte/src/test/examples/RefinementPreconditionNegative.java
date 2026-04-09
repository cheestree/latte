package latte;

import specification.Free;
import specification.Unique;

public class RefinementPreconditionNegative {

    @Unique NodePrecondition root;

    public RefinementPreconditionNegative(@Free NodePrecondition root) {
        this.root = root;
    }

    // @Refinement("arg != null && arg.value != null && _ != null")
    @Free NodePrecondition requireFree(@Free NodePrecondition arg) {
        return new NodePrecondition(new Object(), arg);
    }

    void violatesPrecondition() {
        // fails due to permission mismatch on @Free argument.
        NodePrecondition out = requireFree(this.root);
        this.root = out;
    }

    public static void main(String[] args) {
        NodePrecondition node = new NodePrecondition(new Object(), null);
        RefinementPreconditionNegative test = new RefinementPreconditionNegative(node);
        test.violatesPrecondition();
    }
}

class NodePrecondition {
    @Unique Object value;
    @Unique NodePrecondition next;

    NodePrecondition(@Free Object value, @Free NodePrecondition next) {
        this.value = value;
        this.next = next;
    }
}
