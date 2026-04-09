package latte;

import specification.Borrowed;
import specification.Free;
import specification.Unique;

public class RefinementBorrowedReadPositive {

    @Unique NodeBorrowed root;

    public RefinementBorrowedReadPositive(@Free NodeBorrowed root) {
        this.root = root;
    }

    // reading from a non-null borrowed node returns its payload.
    // @Refinement("(n == null && _ == null) || (n != null && n.value != null && _ == n.value)")
    Object safeRead(@Borrowed NodeBorrowed n) {
        if (n == null) {
            return null;
        }
        return n.value;
    }

    public static void main(String[] args) {
        NodeBorrowed node = new NodeBorrowed(new Object(), null);
        RefinementBorrowedReadPositive test = new RefinementBorrowedReadPositive(node);
        Object result = test.safeRead(node);
        System.out.println(result);
    }
}

class NodeBorrowed {
    @Unique Object value;
    @Unique NodeBorrowed next;

    NodeBorrowed(@Free Object value, @Free NodeBorrowed next) {
        this.value = value;
        this.next = next;
    }
}
