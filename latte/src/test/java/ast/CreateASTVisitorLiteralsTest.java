package ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;

import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.parsing.ParsingException;
import rj_language.parsing.RefinementsParser;

public class CreateASTVisitorLiteralsTest {
    private static Expression parse(String input) {
        try {
            return RefinementsParser.createAST(input);
        } catch (ParsingException e) {
            throw new AssertionError("Parsing failed for input: " + input, e);
        }
    }

    // --- Literals ---

    @Test
    public void parsesIntLiteral() throws ParsingException {
        assertInstanceOf(LiteralInt.class, parse("42"));
        assertEquals(42L, ((LiteralInt) parse("42")).getValue());
    }

    @Test
    public void parsesIntLiteralWithUnderscores() throws ParsingException {
        assertEquals(1_000_000L, ((LiteralInt) parse("1_000_000")).getValue());
    }

    @Test
    public void parsesRealLiteral() throws ParsingException {
        assertInstanceOf(LiteralReal.class, parse("3.14"));
        assertEquals(3.14, ((LiteralReal) parse("3.14")).getValue());
    }

    @Test
    public void parsesBoolLiteral() throws ParsingException {
        assertEquals(true, ((LiteralBoolean) parse("true")).getValue());
        assertEquals(false, ((LiteralBoolean) parse("false")).getValue());
    }

    @Test
    public void parsesStringLiteral() throws ParsingException {
        assertEquals("hello", ((LiteralString) parse("\"hello\"")).getValue());
    }
}
