package rj_language.ast;

public final class LiteralReal extends Expression {
    private final double value;

    public LiteralReal(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitLiteralReal(this);
    }
}
