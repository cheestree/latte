package rj_language.ast;

public final class BinaryExpression extends Expression {
    private final Expression left;
    private final BinaryOperator operator;
    private final Expression right;

    public BinaryExpression(Expression left, BinaryOperator operator, Expression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public Expression getLeft() {
        return left;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public Expression getRight() {
        return right;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitBinaryExpression(this);
    }
}
