package rj_language.ast;

public final class ReturnExpression extends Expression {
    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitReturnExpression(this);
    }
}
