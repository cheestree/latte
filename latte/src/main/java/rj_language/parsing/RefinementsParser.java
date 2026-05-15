package rj_language.parsing;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import rj.grammar.LRJLexer;
import rj.grammar.LRJParser;
import rj_language.ast.Expression;
import rj_language.visitors.CreateASTVisitor;

public class RefinementsParser {
    public static Expression createAST(String input) throws ParsingException {
        ParseTree pt = compile(input);
        CreateASTVisitor visitor = new CreateASTVisitor();
        return visitor.create(pt);
    }

    private static ParseTree compile(String input) throws ParsingException {
        LRJParser parser = createParser(input);
        return parser.prog();
    }

    private static LRJParser createParser(String input) {
        LRJLexer lexer = new LRJLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LRJParser parser = new LRJParser(tokens);
        return parser;
    }
}
