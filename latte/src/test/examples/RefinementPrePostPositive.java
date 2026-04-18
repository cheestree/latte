package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int epoch")
// @StateSet({"empty", "nonEmpty"})
// @StateSet({"stable", "moved"})
public class RefinementPrePostPositive {

    @Unique BoxPrePost head;

    public RefinementPrePostPositive(@Free BoxPrePost head) {
        this.head = head;
    }

    // pushed values are non-null.
    // @Refinement("v != null && this.head != null && this.head.value == v")
    // @StateRefinement(from="stable(this)", to="stable(this) && nonEmpty(this) && epoch(this) == epoch(old(this)) + 1")
    void pushFront(@Free Object v) {
        BoxPrePost oldHead = this.head;
        this.head = null;
        BoxPrePost fresh = new BoxPrePost(v, oldHead);
        this.head = fresh;
    }

    // if a node is returned, its payload is initialized.
    // @Refinement("this.head == null && (_ == null || _.value != null)")
    // @StateRefinement(from="stable(this)", to="moved(this) && empty(this) && epoch(this) == epoch(old(this)) + 1")
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
