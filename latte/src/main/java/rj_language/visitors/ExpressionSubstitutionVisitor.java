package rj_language.visitors;

import rj_language.ast.BinaryExpression;
import rj_language.ast.Expression;
import rj_language.ast.ExpressionVisitor;
import rj_language.ast.FieldAccess;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.OldExpression;
import rj_language.ast.ReturnExpression;
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
            return replacement;
        }
        return new Var(expression.getName());
    }

    @Override
    public Expression visitFieldAccess(FieldAccess expression) {
        Expression receiver = expression.getReceiver();
    
        if (receiver instanceof Var var && var.getName().equals(variable)) {
            if (!(replacement instanceof Var) && !(replacement instanceof FieldAccess)) {
                throw new IllegalArgumentException(String.format(
                    "Cannot substitute '%s' with '%s' as field access receiver — must be a variable or field access",
                    variable,
                    replacement
                ));
            }
        }
        
        return new FieldAccess(
            receiver.accept(this),
            expression.getField()
        );
    }

    @Override
    public Expression visitOldExpression(OldExpression expression) {
        Expression inner = expression.getExpression().accept(this);
        if (!(inner instanceof FieldAccess) && !(inner instanceof Var)) {
            throw new IllegalArgumentException(String.format(
                "old() argument must be a variable or field access after substitution, got: %s",
                inner
            ));
        }
        return new OldExpression(inner);
    }

    @Override
    public Expression visitReturnExpression(ReturnExpression expression) {
        return new ReturnExpression();
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

    @Override
    public Expression visitFunctionInvocation(FunctionInvocation expression) {
        return new FunctionInvocation(
            expression.getName(),
            expression.getArguments().stream()
                .map(arg -> arg.accept(this))
                .toList()
        );
    }
}