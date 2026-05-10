package ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.UnaryExpression;
import rj_language.ast.UnaryOperator;
import rj_language.parsing.ParsingException;
import rj_language.parsing.RefinementsParser;

public class CreateASTVisitorTest {
    private static Expression parse(String input) {
        try {
            return RefinementsParser.createAST(input);
        } catch (ParsingException e) {
            throw new AssertionError("Parsing failed for input: " + input, e);
        }
    }

    @Test
    void intLiteral() {
        Expression e = parse("42");
        assertInstanceOf(LiteralInt.class, e);
        assertEquals(42L, ((LiteralInt) e).getValue());
    }

    @Test
    void intLiteralWithUnderscores() {
        Expression e = parse("1_000_000");
        assertEquals(1_000_000L, ((LiteralInt) e).getValue());
    }

    @Test
    void binaryAdd() {
        BinaryExpression e = (BinaryExpression) parse("1 + 2");
        assertEquals(BinaryOperator.ADD, e.getOperator());
        assertInstanceOf(LiteralInt.class, e.getLeft());
        assertInstanceOf(LiteralInt.class, e.getRight());
    }

    @Test
    void unaryNot() {
        UnaryExpression e = (UnaryExpression) parse("!true");
        assertEquals(UnaryOperator.NOT, e.getOperator());
        assertInstanceOf(LiteralBoolean.class, e.getExpression());
    }

    @Test
    void unaryNegate() {
        UnaryExpression e = (UnaryExpression) parse("-5");
        assertEquals(UnaryOperator.NEGATE, e.getOperator());
    }

    @Test
    void functionCallNoArgs() {
        FunctionInvocation e = (FunctionInvocation) parse("foo()");
        assertEquals("foo", e.getName());
        assertTrue(e.getArguments().isEmpty());
    }

    @Test
    void functionCallWithArgs() {
        FunctionInvocation e = (FunctionInvocation) parse("foo(1, 2)");
        assertEquals(2, e.getArguments().size());
    }
}