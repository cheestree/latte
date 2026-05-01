package rj_language.visitors;

import rj_language.ast.BinaryExpression;
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
import rj_language.ast.Var;

/**
 * Substitutes all occurrences of a variable x by expression e in a predicate rho.
 */
public class ExpressionSubstitutionVisitor implements ExpressionVisitor<Expression> {
    private final String variable;
    private final Expression replacement;

    public ExpressionSubstitutionVisitor(String variable, Expression replacement) {
        if (variable == null || variable.isBlank()) {
            throw new IllegalArgumentException("Variable name cannot be null or blank");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("Replacement expression cannot be null");
        }
        this.variable = variable;
        this.replacement = replacement;
    }

    public static Expression substitute(Expression predicate, String variable, Expression replacement) {
        if (predicate == null) {
            return null;
        }
        return predicate.accept(new ExpressionSubstitutionVisitor(variable, replacement));
    }

    @Override
    public Expression visitBinaryExpression(BinaryExpression expression) {
        return new BinaryExpression(
                expression.getLeft().accept(this),
                expression.getOperator(),
                expression.getRight().accept(this));
    }

    @Override
    public Expression visitUnaryExpression(UnaryExpression expression) {
        return new UnaryExpression(
                expression.getOperator(),
                expression.getExpression().accept(this));
    }

    @Override
    public Expression visitVar(Var expression) {
        if (variable.equals(expression.getName())) {
            return deepCopy(replacement);
        }
        return new Var(expression.getName());
    }

    @Override
    public Expression visitFieldAccess(FieldAccess expression) {
        if (!variable.equals(expression.getReceiver())) {
            return new FieldAccess(expression.getReceiver(), expression.getField());
        }

        if (replacement instanceof Var replacementVar) {
            return new FieldAccess(replacementVar.getName(), expression.getField());
        }

        throw new IllegalArgumentException(
                "Cannot substitute variable '" + variable + "' in field access '"
                        + expression.getReceiver() + "." + expression.getField()
                        + "' with non-variable expression");
    }

    @Override
    public Expression visitOldExpression(OldExpression expression) {
        FieldAccess substituted = (FieldAccess) expression.getFieldAccess().accept(this);
        return new OldExpression(substituted);
    }

    @Override
    public Expression visitResultExpression(ResultExpression expression) {
        return new ResultExpression();
    }

    @Override
    public Expression visitLiteralBoolean(LiteralBoolean expression) {
        return new LiteralBoolean(expression.getValue());
    }

    @Override
    public Expression visitLiteralInt(LiteralInt expression) {
        return new LiteralInt(expression.getValue());
    }

    @Override
    public Expression visitLiteralReal(LiteralReal expression) {
        return new LiteralReal(expression.getValue());
    }

    @Override
    public Expression visitLiteralString(LiteralString expression) {
        return new LiteralString(expression.getValue());
    }

    private static Expression deepCopy(Expression expression) {
        return expression.accept(new ExpressionVisitor<Expression>() {
            @Override
            public Expression visitBinaryExpression(BinaryExpression expression) {
                return new BinaryExpression(
                        expression.getLeft().accept(this),
                        expression.getOperator(),
                        expression.getRight().accept(this));
            }

            @Override
            public Expression visitUnaryExpression(UnaryExpression expression) {
                return new UnaryExpression(
                        expression.getOperator(),
                        expression.getExpression().accept(this));
            }

            @Override
            public Expression visitVar(Var expression) {
                return new Var(expression.getName());
            }

            @Override
            public Expression visitFieldAccess(FieldAccess expression) {
                return new FieldAccess(expression.getReceiver(), expression.getField());
            }

            @Override
            public Expression visitOldExpression(OldExpression expression) {
                return new OldExpression((FieldAccess) expression.getFieldAccess().accept(this));
            }

            @Override
            public Expression visitResultExpression(ResultExpression expression) {
                return new ResultExpression();
            }

            @Override
            public Expression visitLiteralBoolean(LiteralBoolean expression) {
                return new LiteralBoolean(expression.getValue());
            }

            @Override
            public Expression visitLiteralInt(LiteralInt expression) {
                return new LiteralInt(expression.getValue());
            }

            @Override
            public Expression visitLiteralReal(LiteralReal expression) {
                return new LiteralReal(expression.getValue());
            }

            @Override
            public Expression visitLiteralString(LiteralString expression) {
                return new LiteralString(expression.getValue());
            }
        });
    }
}