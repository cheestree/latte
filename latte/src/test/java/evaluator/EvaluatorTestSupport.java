package evaluator;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;

import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.Expression;
import rj_language.visitors.ExpressionPrettyPrinter;

abstract class EvaluatorTestSupport {
    protected TypeEnvironment typeEnv;
    protected SymbolicEnvironment symbEnv;
    protected PermissionEnvironment permEnv;
    protected RefinementPath refinementPath;

    @BeforeEach
    void setEnvironment() {
        typeEnv = new TypeEnvironment();
        symbEnv = new SymbolicEnvironment();
        permEnv = new PermissionEnvironment();
        refinementPath = new RefinementPath();
        typeEnv.enterScope();
        symbEnv.enterScope();
        permEnv.enterScope();
    }

    @AfterEach
    void tearDownEnvironment() {
        refinementPath = null;
        typeEnv.exitScope();
        permEnv.exitScope();
        symbEnv.exitScope();
    }

    protected void assertPrints(Expression expression, String expected) {
        assertEquals(expected, ExpressionPrettyPrinter.print(expression));
    }

    protected void assertPrintsMatching(Expression expression, String regex) {
        assertTrue(ExpressionPrettyPrinter.print(expression).matches(regex));
    }

    protected long countPrintedOccurrences(Expression expression, SymbolicValue value) {
        return Arrays.stream(ExpressionPrettyPrinter.print(expression).split(value.toString(), -1)).count() - 1;
    }

    protected void assertImmutable(int symbolicIndex) {
        assertImmutable(new SymbolicValue(symbolicIndex));
    }

    protected void assertImmutable(SymbolicValue value) {
        assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(value));
    }
}
