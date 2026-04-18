package rj_language.ast;

public final class UnaryExpression extends Expression {
    private final UnaryOperator operator;
    private final Expression expression;

    public UnaryExpression(UnaryOperator operator, Expression expression) {
        this.operator = operator;
        this.expression = expression;
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitUnaryExpression(this);
    }
}
