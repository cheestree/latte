package typechecking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralInt;
import rj_language.ast.Var;
import rj_language.smt.SmtSolver;

public class SmtSolverTest {
    @Test
    public void entailsWhenAssumptionsImplyGoal() {
        SmtSolver solver = new SmtSolver();
        Expression assumptions = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(1));
        Expression goal = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.GT,
            new LiteralInt(0));

        assertTrue(solver.entails(assumptions, goal));
    }

    @Test
    public void notEntailsWhenGoalIsStronger() {
        SmtSolver solver = new SmtSolver();
        Expression assumptions = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(1));
        Expression goal = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(2));

        assertFalse(solver.entails(assumptions, goal));
    }

    @Test
    public void entailsWithNoAssumptionsUsesTrue() {
        SmtSolver solver = new SmtSolver();
        Expression goal = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(1));

        assertFalse(solver.entails(null, goal));
    }
}
