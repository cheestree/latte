package typechecking;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // Assumption: v0 == 1
        // Goal: v0 > 0
        // Counterexample: none
        Expression assumptions = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(1));
        Expression goal = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.GT,
            new LiteralInt(0));

        assertTrue(solver.entails(assumptions, goal));
        SmtSolver.EntailmentResult result = solver.checkEntailment(assumptions, goal);
        assertTrue(result.entailed());
        assertTrue(result.counterexample().isEmpty());
    }

    @Test
    public void returnsCounterexampleWhenFromConditionDoesNotEntailGoal() {
        SmtSolver solver = new SmtSolver();
        // Assumption: v0 == 1
        // Goal: v0 == 2
        // Counterexample: v0 = 1
        Expression assumptions = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(1));
        Expression goal = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(2));

        assertFalse(solver.entails(assumptions, goal));
        SmtSolver.EntailmentResult result = solver.checkEntailment(assumptions, goal);
        assertFalse(result.entailed());
        assertEquals("1", result.counterexample().get("v0"));
    }

    @Test
    public void returnsCounterexampleForArithmeticConjunction() {
        SmtSolver solver = new SmtSolver();
        // Assumption: size >= 1 && delta == size + 1
        // Goal: delta > 3
        // Counterexample: size = 1, delta = 2
        Expression assumptions = new BinaryExpression(
            new BinaryExpression(
                new Var("size"),
                BinaryOperator.GE,
                new LiteralInt(1)),
            BinaryOperator.AND,
            new BinaryExpression(
                new Var("delta"),
                BinaryOperator.EQ,
                new BinaryExpression(
                    new Var("size"),
                    BinaryOperator.ADD,
                    new LiteralInt(1))));
        Expression goal = new BinaryExpression(
            new Var("delta"),
            BinaryOperator.GT,
            new LiteralInt(3));

        SmtSolver.EntailmentResult result = solver.checkEntailment(assumptions, goal);

        assertFalse(result.entailed());
        assertEquals("1", result.counterexample().get("size"));
        assertEquals("2", result.counterexample().get("delta"));
    }

    @Test
    public void entailsWithNoAssumptionsUsesTrue() {
        SmtSolver solver = new SmtSolver();
        // Assumption: none (i.e., true)
        // Goal: v0 == 1
        // Counterexample: false
        Expression goal = new BinaryExpression(
            new Var("v0"),
            BinaryOperator.EQ,
            new LiteralInt(1));

        assertFalse(solver.entails(null, goal));
    }
}
