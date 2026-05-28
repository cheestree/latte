package typechecking.cases;

import specification.lj.StateRefinement;

public class InvocationPreconditionFails {
    @StateRefinement(from = "x < 0")
    int caller(int x) {
        int y;
        y = this.requiresPositive(x);
        return y;
    }

    @StateRefinement(from = "x > 0")
    int requiresPositive(int x) {
        return x;
    }
}
