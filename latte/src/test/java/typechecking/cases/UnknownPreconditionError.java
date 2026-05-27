package typechecking.cases;
import specification.Borrowed;
import specification.lj.StateRefinement;

public class UnknownPreconditionError {
    @StateRefinement(from = "missing == missing")
    void bad(@Borrowed Object x) {

    }
}
