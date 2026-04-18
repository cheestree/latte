package rj_language.ast;

public final class OldExpression extends Expression {
    private final FieldAccess fieldAccess;

    public OldExpression(FieldAccess fieldAccess) {
        this.fieldAccess = fieldAccess;
    }

    public FieldAccess getFieldAccess() {
        return fieldAccess;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitOldExpression(this);
    }
}
