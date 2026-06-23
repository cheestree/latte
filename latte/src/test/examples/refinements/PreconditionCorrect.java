import specification.lj.StateRefinement;

public class PreconditionCorrect {
    static class Checker {
        @StateRefinement(from = "value % 2 == 0")
        void requireEven(int value) {
        }

        @StateRefinement(from = "value > 0")
        void requiresPositive(int value) {
        }

        @StateRefinement(from = "flag == true")
        void requiresTrue(boolean flag) {
        }

        @StateRefinement(from = "text == \"ok\"")
        void requiresText(String text) {
        }
    }

    public static void main(String[] args) {
        Checker checker = new Checker();
        checker.requireEven(2);
        checker.requiresPositive(1);
        checker.requiresTrue(true);
        checker.requiresText("ok");
    }
}
