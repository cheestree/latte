package latte;

import specification.Free;
import specification.Unique;

public class RefinementPreconditionNegative {

    @Unique Node root;

    public RefinementPreconditionNegative(@Free Node root) {
        this.root = root;
    }

    // @Refinement("arg != null && arg.value != null && _ != null")
    @Free Node requireFree(@Free Node arg) {
        return new Node(new Object(), arg);
    }

    void violatesPrecondition() {
        // fails due to permission mismatch on @Free argument.
        Node out = requireFree(this.root);
        this.root = out;
    }
}

class Node {
    @Unique Object value;
    @Unique Node next;

    Node(@Free Object value, @Free Node next) {
        this.value = value;
        this.next = next;
    }
}
