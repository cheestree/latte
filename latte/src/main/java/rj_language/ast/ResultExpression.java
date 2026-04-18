package rj_language.ast;

public final class ResultExpression extends Expression {
    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitResultExpression(this);
    }
}
