package latte;

import specification.Free;
import specification.Unique;

public class RefinementPrePostPositive {

    @Unique BoxPrePost head;

    public RefinementPrePostPositive(@Free BoxPrePost head) {
        this.head = head;
    }

    // pushed values are non-null.
    // @Refinement("v != null && this.head.value == v")
    void pushFront(@Free Object v) {
        BoxPrePost oldHead = this.head;
        this.head = null;
        BoxPrePost fresh = new BoxPrePost(v, oldHead);
        this.head = fresh;
    }

    // if a node is returned, its payload is initialized.
    // @Refinement("_ == null || _.value != null")
    @Free BoxPrePost popFrontOrNull() {
        if (this.head == null) {
            return null;
        }
        BoxPrePost oldHead = this.head;
        this.head = null;
        return oldHead;
    }

    public static void main(String[] args) {
        BoxPrePost box = new BoxPrePost(new Object(), null);
        RefinementPrePostPositive test = new RefinementPrePostPositive(box);
        test.pushFront(new Object());
        BoxPrePost out = test.popFrontOrNull();
        System.out.println(out);
    }
}

class BoxPrePost {
    @Unique Object value;
    @Unique BoxPrePost next;

    BoxPrePost(@Free Object value, @Free BoxPrePost next) {
        this.value = value;
        this.next = next;
    }
}
