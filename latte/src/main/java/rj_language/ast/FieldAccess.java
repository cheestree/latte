package rj_language.ast;

public final class FieldAccess extends Expression {
    private final String receiver;
    private final String field;

    public FieldAccess(String receiver, String field) {
        this.receiver = receiver;
        this.field = field;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getField() {
        return field;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitFieldAccess(this);
    }
}
