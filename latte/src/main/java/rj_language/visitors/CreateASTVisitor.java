package rj_language.visitors;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import rj.grammar.RJParser.ExpContext;
import rj.grammar.RJParser.FieldAccessContext;
import rj.grammar.RJParser.FunctionCallContext;
import rj.grammar.RJParser.LiteralContext;
import rj.grammar.RJParser.PrimaryContext;
import rj.grammar.RJParser.ProgContext;
import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.FieldAccess;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.OldExpression;
import rj_language.ast.ReturnExpression;
import rj_language.ast.UnaryExpression;
import rj_language.ast.UnaryOperator;
import rj_language.ast.Var;

public class CreateASTVisitor {
    public CreateASTVisitor() {

    }

    public Expression create(ParseTree pt) {
        if (pt == null)
            return null;

        if (pt instanceof ProgContext progContext)
            return progCreate(progContext);
        else if (pt instanceof ExpContext expContext)
            return expCreate(expContext);
        else if (pt instanceof PrimaryContext primaryContext)
            return primaryCreate(primaryContext);
        else if (pt instanceof FieldAccessContext fieldAccessContext)
            return fieldAccessCreate(fieldAccessContext);
        else if (pt instanceof FunctionCallContext functionCallContext)
            return functionCallCreate(functionCallContext);
        else if (pt instanceof LiteralContext literalContext)
            return literalCreate(literalContext);
        else
            throw new IllegalArgumentException("Unsupported parse tree node: " + pt.getClass().getName());
    }

    private Expression progCreate(ProgContext rc) {
        if (rc.exp() != null)
            return create(rc.exp());
        return null;
    }

    private Expression expCreate(ExpContext rc) {
        if (rc.getChild(0) instanceof TerminalNode first) {
            if (first.getSymbol().getType() == rj.grammar.RJLexer.NOT) {
                Expression inner = create(rc.exp(0));
                return new UnaryExpression(UnaryOperator.NOT, inner);
            } else if (first.getSymbol().getType() == rj.grammar.RJLexer.MINUS && rc.getChildCount() == 2) {
                Expression inner = create(rc.exp(0));
                return new UnaryExpression(UnaryOperator.NEGATE, inner);
            }
        }

        if (rc.getChildCount() == 3) {
            Expression left = create(rc.exp(0));
            String opText = rc.getChild(1).getText();
            Expression right = create(rc.exp(1));

            BinaryOperator op = switch (opText) {
                case "*" -> BinaryOperator.MUL;
                case "/" -> BinaryOperator.DIV;
                case "%" -> BinaryOperator.MOD;
                case "+" -> BinaryOperator.ADD;
                case "-" -> BinaryOperator.SUB;
                case "<" -> BinaryOperator.LT;
                case ">" -> BinaryOperator.GT;
                case "<=" -> BinaryOperator.LE;
                case ">=" -> BinaryOperator.GE;
                case "==" -> BinaryOperator.EQ;
                case "!=" -> BinaryOperator.NEQ;
                case "&&" -> BinaryOperator.AND;
                case "||" -> BinaryOperator.OR;
                default -> throw new IllegalStateException("Unsupported operator: " + opText);
            };

            return new BinaryExpression(left, op, right);
        }

        if (rc.primary() != null) return create(rc.primary());

        throw new IllegalStateException("Unsupported expression: " + rc.getText());
    }

    private Expression primaryCreate(PrimaryContext rc) {
        if (rc.literal() != null)
            return create(rc.literal());
        if (rc.RETURN() != null)
            return new ReturnExpression();
        if (rc.functionCall() != null)
            return create(rc.functionCall());
        if (rc.fieldAccess() != null)
            return create(rc.fieldAccess());
        if (rc.ID() != null)
            return new Var(rc.ID().getText());
        if (rc.exp() != null)
            return create(rc.exp());

        throw new IllegalStateException("Unsupported primary expression: " + rc.getText());
    }

    private Expression functionCallCreate(FunctionCallContext rc) {
        String name = rc.ID().getText();

        List<Expression> arguments =
            rc.args() == null
                ? List.of()
                : rc.args().exp().stream()
                    .map(this::create)
                    .toList();

        if (name.equals("old")) {
            Expression arg = arguments.get(0);
            if (!(arg instanceof FieldAccess))
                throw new IllegalStateException(
                    "old() argument must be a field access like old(this.f) or old(x.f), got: " + arg
            );
            return new OldExpression(arg);
        }

        return new FunctionInvocation(name, arguments);
    }

    private Expression fieldAccessCreate(FieldAccessContext rc) {
        return new FieldAccess(
            new Var(rc.ID(0).getText()),
            rc.ID(1).getText()
        );
    }

    private Expression literalCreate(LiteralContext rc) {
        if (rc.BOOL() != null)
            return new LiteralBoolean(Boolean.parseBoolean(rc.BOOL().getText()));
        if (rc.STRING() != null) {
            String text = rc.STRING().getText();
            return new LiteralString(text.substring(1, text.length() - 1));
        }
        if (rc.INT() != null) {
            String normalized = rc.INT().getText().replace("_", "");
            return new LiteralInt(Long.parseLong(normalized));
        }
        if (rc.REAL() != null)
            return new LiteralReal(Double.parseDouble(rc.REAL().getText()));

        throw new IllegalStateException("Unsupported literal: " + rc.getText());
    }
}
