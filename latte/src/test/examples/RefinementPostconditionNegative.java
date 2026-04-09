package latte;

import specification.Free;
import specification.Unique;

public class RefinementPostconditionNegative {

    @Unique Node root;

    public RefinementPostconditionNegative(@Free Node root) {
        this.root = root;
    }

    // @Refinement("_ == null || _.value != null")
    @Free Node brokenReturn() {
        // returns an existing field, violating the @Free return contract.
        return this.root;
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
