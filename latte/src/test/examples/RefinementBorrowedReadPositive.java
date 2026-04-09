package latte;

import specification.Borrowed;
import specification.Free;
import specification.Unique;

public class RefinementBorrowedReadPositive {

    @Unique Node root;

    public RefinementBorrowedReadPositive(@Free Node root) {
        this.root = root;
    }

    // reading from a non-null borrowed node returns its payload.
    // @Refinement("(n == null && _ == null) || (n != null && n.value != null && _ == n.value)")
    Object safeRead(@Borrowed Node n) {
        if (n == null) {
            return null;
        }
        return n.value;
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
