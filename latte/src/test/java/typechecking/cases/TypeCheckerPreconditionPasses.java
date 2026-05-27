package typechecking.cases;
import specification.Borrowed;
import specification.lj.Ghost;
import specification.lj.StateRefinement;

public class TypeCheckerPreconditionPasses {
    @StateRefinement(from = "x == x")
    void borrowedParam(@Borrowed Object x) {

    }

    static class Box {
        @Ghost
        boolean ready;

        @StateRefinement(from = "x == x")
        Box(@Borrowed Object x) {

        }

        @StateRefinement(from = "this.ready == false")
        void zeroParamThisField() {

        }
    }
}
