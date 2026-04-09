import specification.Free;
import specification.Unique;

public class RefinementPrePostPositive {

    @Unique Box head;

    public RefinementPrePostPositive(@Free Box head) {
        this.head = head;
    }

    // pushed values are non-null.
    // @Refinement("v != null && this.head.value == v")
    void pushFront(@Free Object v) {
        Box oldHead = this.head;
        this.head = null;
        Box fresh = new Box(v, oldHead);
        this.head = fresh;
    }

    // if a node is returned, its payload is initialized.
    // @Refinement("_ == null || _.value != null")
    @Free Box popFrontOrNull() {
        if (this.head == null) {
            return null;
        }
        Box oldHead = this.head;
        this.head = null;
        return oldHead;
    }
}

class Box {
    @Unique Object value;
    @Unique Box next;

    Box(@Free Object value, @Free Box next) {
        this.value = value;
        this.next = next;
    }
}
