import specification.lj.StateRefinement;

public class PreconditionError {
    static class Checker {
        @StateRefinement(from = "value > 0")
        void requiresPositive(int value) {
        }
    }

    public static void main(String[] args) {
        Checker checker = new Checker();
        checker.requiresPositive(0);
    }
}
