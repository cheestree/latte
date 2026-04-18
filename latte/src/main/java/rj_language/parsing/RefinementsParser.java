package rj_language.parsing;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import rj.grammar.RJLexer;
import rj.grammar.RJParser;
import rj_language.ast.Expression;
import rj_language.visitors.CreateASTVisitor;

public class RefinementsParser {
    public static Expression createAST(String input) throws ParsingException {
        ParseTree pt = compile(input);
        CreateASTVisitor visitor = new CreateASTVisitor();
        return visitor.create(pt);
    }

    private static ParseTree compile(String input) throws ParsingException {
        RJParser parser = createParser(input);
        return parser.prog();
    }

    private static RJParser createParser(String input) {
        RJLexer lexer = new RJLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RJParser parser = new RJParser(tokens);
        return parser;
    }
}
