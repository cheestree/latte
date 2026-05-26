package typechecking.cases;
import specification.Unique;
import specification.lj.StateRefinement;

public class SharedReceiverUniqueFieldPredicateError {
    static class Box {
        @Unique
        Object value;
    }

    // box has no permission annot., so it defaults to shared.
    // the value field is non-shared, so it cannot be accessed in the predicate of a shared receiver.
    @StateRefinement(from = "box.value == box.value")
    void bad(Box box) {

    }
}
