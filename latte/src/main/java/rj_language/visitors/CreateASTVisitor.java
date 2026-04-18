package rj_language.visitors;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import rj.grammar.RJParser.AdditiveExpContext;
import rj.grammar.RJParser.EqualityExpContext;
import rj.grammar.RJParser.ExpressionContext;
import rj.grammar.RJParser.FieldAccessContext;
import rj.grammar.RJParser.LiteralContext;
import rj.grammar.RJParser.LogicalAndExpContext;
import rj.grammar.RJParser.LogicalOrExpContext;
import rj.grammar.RJParser.OldExpContext;
import rj.grammar.RJParser.PrimaryContext;
import rj.grammar.RJParser.ProgContext;
import rj.grammar.RJParser.RelationalExpContext;
import rj.grammar.RJParser.UnaryExpContext;
import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.FieldAccess;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.OldExpression;
import rj_language.ast.ResultExpression;
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
        else if (pt instanceof ExpressionContext expressionContext)
            return expressionCreate(expressionContext);
        else if (pt instanceof LogicalOrExpContext logicalOrExpContext)
            return logicalOrExpCreate(logicalOrExpContext);
        else if (pt instanceof LogicalAndExpContext logicalAndExpContext)
            return logicalAndExpCreate(logicalAndExpContext);
        else if (pt instanceof EqualityExpContext equalityExpContext)
            return equalityExpCreate(equalityExpContext);
        else if (pt instanceof RelationalExpContext relationalExpContext)
            return relationalExpCreate(relationalExpContext);
        else if (pt instanceof AdditiveExpContext additiveExpContext)
            return additiveExpCreate(additiveExpContext);
        else if (pt instanceof UnaryExpContext unaryExpContext)
            return unaryExpCreate(unaryExpContext);
        else if (pt instanceof PrimaryContext primaryContext)
            return primaryCreate(primaryContext);
        else if (pt instanceof OldExpContext oldExpContext)
            return oldExpCreate(oldExpContext);
        else if (pt instanceof FieldAccessContext fieldAccessContext)
            return fieldAccessCreate(fieldAccessContext);
        else if (pt instanceof LiteralContext literalContext)
            return literalCreate(literalContext);
        else
            throw new IllegalArgumentException("Unsupported parse tree node: " + pt.getClass().getName());
    }

    private Expression progCreate(ProgContext rc) {
        if (rc.expression() != null)
            return create(rc.expression());
        return null;
    }

    private Expression expressionCreate(ExpressionContext rc) {
        if (rc.logicalOrExp() != null)
            return create(rc.logicalOrExp());
        return null;
    }

    private Expression logicalOrExpCreate(LogicalOrExpContext rc) {
        List<LogicalAndExpContext> terms = rc.logicalAndExp();
        if (terms.isEmpty())
            return null;

        Expression result = create(terms.get(0));
        for (int i = 1; i < terms.size(); i++) {
            result = new BinaryExpression(result, BinaryOperator.OR, create(terms.get(i)));
        }
        return result;
    }

    private Expression logicalAndExpCreate(LogicalAndExpContext rc) {
        List<EqualityExpContext> terms = rc.equalityExp();
        if (terms.isEmpty())
            return null;

        Expression result = create(terms.get(0));
        for (int i = 1; i < terms.size(); i++) {
            result = new BinaryExpression(result, BinaryOperator.AND, create(terms.get(i)));
        }
        return result;
    }

    private Expression equalityExpCreate(EqualityExpContext rc) {
        Expression left = create(rc.relationalExp(0));
        if (rc.relationalExp().size() == 1)
            return left;

        Expression right = create(rc.relationalExp(1));
        if (rc.EQ() != null)
            return new BinaryExpression(left, BinaryOperator.EQ, right);
        if (rc.NEQ() != null) {
            Expression eq = new BinaryExpression(left, BinaryOperator.EQ, right);
            return new UnaryExpression(UnaryOperator.NOT, eq);
        }

        throw new IllegalStateException("Unsupported equality operator in: " + rc.getText());
    }

    private Expression relationalExpCreate(RelationalExpContext rc) {
        Expression left = create(rc.additiveExp(0));
        if (rc.additiveExp().size() == 1)
            return left;

        Expression right = create(rc.additiveExp(1));
        if (rc.LT() != null)
            return new BinaryExpression(left, BinaryOperator.LT, right);
        if (rc.GE() != null)
            return new BinaryExpression(left, BinaryOperator.GE, right);
        if (rc.GT() != null)
            return new BinaryExpression(right, BinaryOperator.LT, left);
        if (rc.LE() != null)
            return new BinaryExpression(right, BinaryOperator.GE, left);

        throw new IllegalStateException("Unsupported relational operator in: " + rc.getText());
    }

    private Expression additiveExpCreate(AdditiveExpContext rc) {
        List<UnaryExpContext> terms = rc.unaryExp();
        if (terms.isEmpty())
            return null;

        Expression result = create(terms.get(0));
        for (int i = 1; i < terms.size(); i++) {
            String op = rc.getChild(2 * i - 1).getText();
            BinaryOperator operator = "+".equals(op) ? BinaryOperator.ADD : BinaryOperator.SUB;
            result = new BinaryExpression(result, operator, create(terms.get(i)));
        }
        return result;
    }

    private Expression unaryExpCreate(UnaryExpContext rc) {
        if (rc.primary() != null)
            return create(rc.primary());

        Expression inner = create(rc.unaryExp());
        if (rc.NOT() != null)
            return new UnaryExpression(UnaryOperator.NOT, inner);
        if (rc.MINUS() != null)
            return new UnaryExpression(UnaryOperator.NEGATE, inner);

        throw new IllegalStateException("Unsupported unary expression: " + rc.getText());
    }

    private Expression primaryCreate(PrimaryContext rc) {
        if (rc.literal() != null)
            return create(rc.literal());
        if (rc.RESULT() != null)
            return new ResultExpression();
        if (rc.oldExp() != null)
            return create(rc.oldExp());
        if (rc.fieldAccess() != null)
            return create(rc.fieldAccess());
        if (rc.ID() != null)
            return new Var(rc.ID().getText());
        if (rc.expression() != null)
            return create(rc.expression());

        throw new IllegalStateException("Unsupported primary expression: " + rc.getText());
    }

    private Expression oldExpCreate(OldExpContext rc) {
        Expression field = create(rc.fieldAccess());
        if (field instanceof FieldAccess fieldAccess)
            return new OldExpression(fieldAccess);

        throw new IllegalStateException("old(...) expects a field access but got: " + rc.getText());
    }

    private Expression fieldAccessCreate(FieldAccessContext rc) {
        return new FieldAccess(rc.ID(0).getText(), rc.ID(1).getText());
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
