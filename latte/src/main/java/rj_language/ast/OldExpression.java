package rj_language.ast;

public final class OldExpression extends Expression {
    private final Expression expression;

    public OldExpression(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "old(" + expression + ")";
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitOldExpression(this);
    }
}