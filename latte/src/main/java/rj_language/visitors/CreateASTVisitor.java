package rj_language.visitors;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import rj.grammar.LRJParser.ExpAddContext;
import rj.grammar.LRJParser.ExpAndContext;
import rj.grammar.LRJParser.ExpEqContext;
import rj.grammar.LRJParser.ExpFieldAccContext;
import rj.grammar.LRJParser.ExpFunCallContext;
import rj.grammar.LRJParser.ExpMultContext;
import rj.grammar.LRJParser.ExpOrContext;
import rj.grammar.LRJParser.ExpPrimContext;
import rj.grammar.LRJParser.ExpRelContext;
import rj.grammar.LRJParser.ExpUnaryContext;
import rj.grammar.LRJParser.LiteralContext;
import rj.grammar.LRJParser.PrimFieldAccContext;
import rj.grammar.LRJParser.PrimFunCallContext;
import rj.grammar.LRJParser.PrimIdContext;
import rj.grammar.LRJParser.PrimLitContext;
import rj.grammar.LRJParser.PrimParenContext;
import rj.grammar.LRJParser.PrimRetContext;
import rj.grammar.LRJParser.ProgContext;
import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.FieldAccess;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.ReturnExpression;
import rj_language.ast.UnaryExpression;
import rj_language.ast.UnaryOperator;
import rj_language.ast.Var;

public class CreateASTVisitor {
    public CreateASTVisitor() {}

    public Expression create(ParseTree pt) {
        if (pt == null) return null;

        if (pt instanceof ProgContext c) return progCreate(c);
        if (pt instanceof ExpUnaryContext c) return expUnaryCreate(c);
        if (pt instanceof ExpMultContext c) return expBinaryCreate(c);
        if (pt instanceof ExpAddContext c) return expBinaryCreate(c);
        if (pt instanceof ExpRelContext c) return expBinaryCreate(c);
        if (pt instanceof ExpEqContext c) return expBinaryCreate(c);
        if (pt instanceof ExpAndContext c) return expBinaryCreate(c);
        if (pt instanceof ExpOrContext c) return expBinaryCreate(c);
        if (pt instanceof ExpPrimContext c) return create(c.primary());
        if (pt instanceof PrimLitContext c) return create(c.literal());
        if (pt instanceof PrimRetContext c) return new ReturnExpression();
        if (pt instanceof PrimFunCallContext c) return create(c.functionCall());
        if (pt instanceof PrimFieldAccContext c) return create(c.fieldAccess());
        if (pt instanceof PrimIdContext c) return new Var(c.ID().getText());
        if (pt instanceof PrimParenContext c) return create(c.exp());
        if (pt instanceof ExpFunCallContext c) return functionCallCreate(c);
        if (pt instanceof ExpFieldAccContext c) return fieldAccessCreate(c);
        if (pt instanceof LiteralContext c) return literalCreate(c);
        throw new IllegalArgumentException("Unsupported parse tree node: " + pt.getClass().getName());
    }

    private Expression progCreate(ProgContext rc) {
        return rc.exp() != null ? create(rc.exp()) : null;
    }

    private Expression expUnaryCreate(ExpUnaryContext rc) {
        Expression operand = create(rc.exp());
        UnaryOperator op = switch (rc.getChild(0).getText()) {
            case "!" -> UnaryOperator.NOT;
            case "-" -> UnaryOperator.NEGATE;
            default  -> throw new IllegalStateException(
                "Unsupported unary operator: " + rc.getChild(0).getText()
            );
        };
        return new UnaryExpression(op, operand);
    }

    private Expression expBinaryCreate(ParserRuleContext rc) {
        return new BinaryExpression(
            create(rc.getChild(0)),
            BinaryOperator.fromSymbol(rc.getChild(1).getText()),
            create(rc.getChild(2))
        );
    }

    private Expression functionCallCreate(ExpFunCallContext rc) {
        String name = rc.ID().getText();

        List<Expression> arguments = rc.args() == null
            ? List.of()
            : rc.args().exp().stream()
                .map(this::create)
                .toList();

        return new FunctionInvocation(name, arguments);
    }

    private Expression fieldAccessCreate(ExpFieldAccContext rc) {
        return new FieldAccess(
            new Var(rc.ID(0).getText()),
            rc.ID(1).getText()
        );
    }

    private Expression literalCreate(LiteralContext rc) {
        if (rc.BOOL() != null) return new LiteralBoolean(Boolean.parseBoolean(rc.BOOL().getText()));
        if (rc.INT() != null) return new LiteralInt(Long.parseLong(rc.INT().getText().replace("_", "")));
        if (rc.REAL() != null) return new LiteralReal(Double.parseDouble(rc.REAL().getText()));
        if (rc.STRING() != null) {
            String text = rc.STRING().getText();
            return new LiteralString(text.substring(1, text.length() - 1));
        }
        throw new IllegalStateException("Unsupported literal: " + rc.getText());
    }
}
