package rj_language.ast;

public abstract class Expression {
    public abstract <T> T accept(ExpressionVisitor<T> visitor);
}
