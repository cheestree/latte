package ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralInt;
import rj_language.ast.ReturnExpression;
import rj_language.ast.UnaryExpression;
import rj_language.ast.UnaryOperator;
import rj_language.ast.Var;
import rj_language.parsing.ParsingException;
import rj_language.parsing.RefinementsParser;

public class CreateASTVisitorNaryTest {
    private static Expression parse(String input) {
        try {
            return RefinementsParser.createAST(input);
        } catch (ParsingException e) {
            throw new AssertionError("Parsing failed for input: " + input, e);
        }
    }

    // --- Variables ---

    @Test
    void parsesVar() {
        Var v = (Var) parse("x");
        assertEquals("x", v.getName());
    }

    @Test
    void parsesReturnExpression() {
        assertInstanceOf(ReturnExpression.class, parse("return"));
    }

    // --- Unary ---

    @Test
    void parsesUnaryNot() {
        UnaryExpression e = (UnaryExpression) parse("!x");
        assertEquals(UnaryOperator.NOT, e.getOperator());
        assertEquals("x", ((Var) e.getExpression()).getName());
    }

    @Test
    void parsesUnaryNegate() {
        UnaryExpression e = (UnaryExpression) parse("-x");
        assertEquals(UnaryOperator.NEGATE, e.getOperator());
    }

    // --- Binary ---

    @Test
    void parsesBinaryAdd() {
        BinaryExpression e = (BinaryExpression) parse("x + 1");
        assertEquals(BinaryOperator.ADD, e.getOperator());
        assertEquals("x", ((Var) e.getLeft()).getName());
        assertEquals(1L, ((LiteralInt) e.getRight()).getValue());
    }

    @Test
    void parsesBinaryComparison() {
        BinaryExpression e = (BinaryExpression) parse("x > 0");
        assertEquals(BinaryOperator.GT, e.getOperator());
    }

    @Test
    void parsesBinaryLogical() {
        BinaryExpression e = (BinaryExpression) parse("x > 0 && y < 5");
        assertEquals(BinaryOperator.AND, e.getOperator());
    }
}
