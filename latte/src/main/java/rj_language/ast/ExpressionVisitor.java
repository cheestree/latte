package rj_language.ast;

public interface ExpressionVisitor<T> {
    T visitBinaryExpression(BinaryExpression expression);

    T visitUnaryExpression(UnaryExpression expression);

    T visitVar(Var expression);

    T visitFieldAccess(FieldAccess expression);

    T visitResultExpression(ReturnExpression expression);

    T visitLiteralBoolean(LiteralBoolean expression);

    T visitLiteralInt(LiteralInt expression);

    T visitLiteralReal(LiteralReal expression);

    T visitLiteralString(LiteralString expression);

    T visitFunctionInvocation(FunctionInvocation expression);

    T visitOldExpression(OldExpression expression);
}
