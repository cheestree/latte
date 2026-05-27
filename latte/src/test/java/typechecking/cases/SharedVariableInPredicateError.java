package typechecking.cases;
import specification.lj.StateRefinement;

public class SharedVariableInPredicateError {
    // s has no permission annot., so it defaults to shared.
    // Predicate is s == s, which isn't valid as s needs permission higher than shared.
    @StateRefinement(from = "s == s")
    void bad(String s) {

    }
}
