package latte;

import specification.Free;
import specification.Unique;

public class RefinementPostconditionNegative {

    @Unique NodePostcondition root;

    public RefinementPostconditionNegative(@Free NodePostcondition root) {
        this.root = root;
    }

    // @Refinement("_ == null || _.value != null")
    @Free NodePostcondition brokenReturn() {
        // returns an existing field, violating the @Free return contract.
        return this.root;
    }

    public static void main(String[] args) {
        NodePostcondition node = new NodePostcondition(new Object(), null);
        RefinementPostconditionNegative test = new RefinementPostconditionNegative(node);
        NodePostcondition out = test.brokenReturn();
        System.out.println(out);
    }
}

class NodePostcondition {
    @Unique Object value;
    @Unique NodePostcondition next;

    NodePostcondition(@Free Object value, @Free NodePostcondition next) {
        this.value = value;
        this.next = next;
    }
}
