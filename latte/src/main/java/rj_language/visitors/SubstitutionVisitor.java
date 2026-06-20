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
import rj_language.ast.ReturnExpression;
import rj_language.ast.UnaryExpression;
import rj_language.ast.Var;

public class SubstitutionVisitor implements ExpressionVisitor<Expression> {
    public String varName;
    public Expression replacement;

    public SubstitutionVisitor(String varName, Expression replacement) {
        this.varName = varName;
        this.replacement = replacement;
    }

    @Override
    public Expression visitBinaryExpression(BinaryExpression expression) {
        return new BinaryExpression(
            expression.getLeft().accept(this),
            expression.getOperator(),
            expression.getRight().accept(this)
        );
    }

    @Override
    public Expression visitUnaryExpression(UnaryExpression expression) {
        return new UnaryExpression(expression.getOperator(), expression.getExpression().accept(this));
    }

    @Override
    public Expression visitVar(Var e) {
        return e.getName().equals(varName) ? replacement : e;
    }

    @Override
    public Expression visitReturnExpression(ReturnExpression e) {
        return varName.equals("return") ? replacement : e;
    }

    @Override
    public Expression visitLiteralInt(LiteralInt e) { return e; }
    @Override
    public Expression visitLiteralBoolean(LiteralBoolean e) { return e; }
    @Override
    public Expression visitLiteralReal(LiteralReal e) { return e; }
    @Override
    public Expression visitLiteralString(LiteralString e) { return e; }

    @Override
    public Expression visitFunctionInvocation(FunctionInvocation expression) {
        throw new UnsupportedOperationException("Substitution on function invocation on return not supported.");
    }

    @Override
    public Expression visitFieldAccess(FieldAccess expression) {
        return new FieldAccess(
            expression.getReceiver().accept(this),
            expression.getField()
        );
    }
}
