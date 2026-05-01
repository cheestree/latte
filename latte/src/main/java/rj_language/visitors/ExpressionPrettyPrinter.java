package rj_language.visitors;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.ExpressionVisitor;
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

/**
 * Converts a refinement AST expression back to a normalized textual representation.
 */
public class ExpressionPrettyPrinter implements ExpressionVisitor<String> {

    public static String print(Expression expression) {
        if (expression == null) {
            return null;
        }
        return expression.accept(new ExpressionPrettyPrinter());
    }

    @Override
    public String visitBinaryExpression(BinaryExpression expression) {
        return "(" + expression.getLeft().accept(this)
                + " " + symbol(expression.getOperator()) + " "
                + expression.getRight().accept(this) + ")";
    }

    @Override
    public String visitUnaryExpression(UnaryExpression expression) {
        if (expression.getOperator() == UnaryOperator.NOT) {
            return "!" + parenthesizeIfNeeded(expression.getExpression());
        }
        return "-" + parenthesizeIfNeeded(expression.getExpression());
    }

    @Override
    public String visitVar(Var expression) {
        return expression.getName();
    }

    @Override
    public String visitFieldAccess(FieldAccess expression) {
        return expression.getReceiver() + "." + expression.getField();
    }

    @Override
    public String visitOldExpression(OldExpression expression) {
        return "old(" + expression.getFieldAccess().accept(this) + ")";
    }

    @Override
    public String visitResultExpression(ResultExpression expression) {
        return "result";
    }

    @Override
    public String visitLiteralBoolean(LiteralBoolean expression) {
        return Boolean.toString(expression.getValue());
    }

    @Override
    public String visitLiteralInt(LiteralInt expression) {
        return Long.toString(expression.getValue());
    }

    @Override
    public String visitLiteralReal(LiteralReal expression) {
        return Double.toString(expression.getValue());
    }

    @Override
    public String visitLiteralString(LiteralString expression) {
        return "\"" + expression.getValue().replace("\"", "\\\"") + "\"";
    }

    private static String symbol(BinaryOperator operator) {
        return switch (operator) {
            case ADD -> "+";
            case SUB -> "-";
            case EQ -> "==";
            case LT -> "<";
            case GE -> ">=";
            case AND -> "&&";
            case OR -> "||";
        };
    }

    private String parenthesizeIfNeeded(Expression expression) {
        if (expression == null) {
            return "";
        }
        if (expression instanceof BinaryExpression) {
            return "(" + expression.accept(this) + ")";
        }
        return expression.accept(this);
    }
}