package rj_language.smt;

import java.util.LinkedHashMap;
import java.util.Map;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Sort;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.UnaryExpression;
import rj_language.ast.UnaryOperator;
import rj_language.ast.Var;

/**
 * Encodes the core refinement expression language as Z3 expressions.
 *
 * <p>Predicates should be evaluated before reaching this encoder, so field
 * accesses, function invocations, and {@code result} are intentionally not
 * supported here.</p>
 */
final class SmtEncoder {
    private final Context context;
    private final Map<String, Sort> symbolSorts = new LinkedHashMap<>();
    private final Map<String, Expr<?>> symbols = new LinkedHashMap<>();

    SmtEncoder(Context context) {
        this.context = context;
    }

    BoolExpr encodeBoolean(Expression expression) {
        if (expression instanceof LiteralBoolean literal) {
            return context.mkBool(literal.getValue());
        }
        if (expression instanceof Var variable) {
            return (BoolExpr) symbol(variable.getName(), context.getBoolSort());
        }
        if (expression instanceof UnaryExpression unary) {
            if (unary.getOperator() != UnaryOperator.NOT) {
                throw unsupported(expression, "Expected a boolean unary expression");
            }
            return context.mkNot(encodeBoolean(unary.getExpression()));
        }
        if (expression instanceof BinaryExpression binary) {
            return encodeBooleanBinary(binary);
        }
        throw unsupported(expression, "Expected a boolean expression");
    }

    private BoolExpr encodeBooleanBinary(BinaryExpression binary) {
        return switch (binary.getOperator()) {
            case AND -> context.mkAnd(
                encodeBoolean(binary.getLeft()),
                encodeBoolean(binary.getRight())
            );
            case OR -> context.mkOr(
                encodeBoolean(binary.getLeft()),
                encodeBoolean(binary.getRight())
            );
            case EQ, NEQ -> encodeEquality(binary);
            case LT, LE, GT, GE -> encodeComparison(binary);
            default -> throw unsupported(binary, "Operator does not produce a boolean");
        };
    }

    private BoolExpr encodeEquality(BinaryExpression binary) {
        Sort sort = inferEqualitySort(binary.getLeft(), binary.getRight());
        Expr<?> left = encodeValue(binary.getLeft(), sort);
        Expr<?> right = encodeValue(binary.getRight(), sort);
        BoolExpr equality = context.mkEq(left, right);
        return binary.getOperator() == BinaryOperator.EQ ? equality : context.mkNot(equality);
    }

    private BoolExpr encodeComparison(BinaryExpression binary) {
        Sort sort = inferNumericSort(binary.getLeft(), binary.getRight());
        ArithExpr<?> left = encodeArithmetic(binary.getLeft(), sort);
        ArithExpr<?> right = encodeArithmetic(binary.getRight(), sort);

        return switch (binary.getOperator()) {
            case LT -> context.mkLt(left, right);
            case LE -> context.mkLe(left, right);
            case GT -> context.mkGt(left, right);
            case GE -> context.mkGe(left, right);
            default -> throw new IllegalStateException("Expected a comparison operator");
        };
    }

    private Expr<?> encodeValue(Expression expression, Sort expectedSort) {
        if (expectedSort.equals(context.getBoolSort())) {
            return encodeBoolean(expression);
        }
        if (expectedSort.equals(context.getStringSort())) {
            return encodeString(expression);
        }
        if (expectedSort.equals(context.getIntSort()) || expectedSort.equals(context.getRealSort())) {
            return encodeArithmetic(expression, expectedSort);
        }
        throw new IllegalStateException("Unsupported SMT sort: " + expectedSort);
    }

    private ArithExpr<?> encodeArithmetic(Expression expression, Sort expectedSort) {
        if (expression instanceof LiteralInt literal) {
            if (expectedSort.equals(context.getRealSort())) {
                return context.mkReal(Long.toString(literal.getValue()));
            }
            return context.mkInt(literal.getValue());
        }
        if (expression instanceof LiteralReal literal) {
            if (!expectedSort.equals(context.getRealSort())) {
                throw unsupported(expression, "Real literal used in an integer expression");
            }
            return context.mkReal(Double.toString(literal.getValue()));
        }
        if (expression instanceof Var variable) {
            return (ArithExpr<?>) symbol(variable.getName(), expectedSort);
        }
        if (expression instanceof UnaryExpression unary) {
            if (unary.getOperator() != UnaryOperator.NEGATE) {
                throw unsupported(expression, "Expected an arithmetic unary expression");
            }
            return context.mkUnaryMinus(encodeArithmetic(unary.getExpression(), expectedSort));
        }
        if (expression instanceof BinaryExpression binary && isArithmetic(binary.getOperator())) {
            ArithExpr<?> left = encodeArithmetic(binary.getLeft(), expectedSort);
            ArithExpr<?> right = encodeArithmetic(binary.getRight(), expectedSort);
            return switch (binary.getOperator()) {
                case ADD -> context.mkAdd(left, right);
                case SUB -> context.mkSub(left, right);
                case MUL -> context.mkMul(left, right);
                case DIV -> context.mkDiv(left, right);
                case MOD -> context.mkMod(asInteger(left), asInteger(right));
                default -> throw new IllegalStateException("Expected an arithmetic operator");
            };
        }
        throw unsupported(expression, "Expected an arithmetic expression");
    }

    private Expr<?> encodeString(Expression expression) {
        if (expression instanceof LiteralString literal) {
            return context.mkString(literal.getValue());
        }
        if (expression instanceof Var variable) {
            return symbol(variable.getName(), context.getStringSort());
        }
        throw unsupported(expression, "Expected a string expression");
    }

    private Sort inferEqualitySort(Expression left, Expression right) {
        if (isBoolean(left) || isBoolean(right)) {
            return context.getBoolSort();
        }
        if (left instanceof LiteralString || right instanceof LiteralString) {
            return context.getStringSort();
        }
        return inferNumericSort(left, right);
    }

    private Sort inferNumericSort(Expression left, Expression right) {
        if (containsReal(left) || containsReal(right)) {
            return context.getRealSort();
        }
        return context.getIntSort();
    }

    private boolean isBoolean(Expression expression) {
        if (expression instanceof LiteralBoolean) {
            return true;
        }
        if (expression instanceof UnaryExpression unary) {
            return unary.getOperator() == UnaryOperator.NOT;
        }
        if (expression instanceof BinaryExpression binary) {
            return switch (binary.getOperator()) {
                case AND, OR, EQ, NEQ, LT, LE, GT, GE -> true;
                default -> false;
            };
        }
        return false;
    }

    private boolean containsReal(Expression expression) {
        if (expression instanceof LiteralReal) {
            return true;
        }
        if (expression instanceof UnaryExpression unary) {
            return containsReal(unary.getExpression());
        }
        if (expression instanceof BinaryExpression binary) {
            return containsReal(binary.getLeft()) || containsReal(binary.getRight());
        }
        return false;
    }

    private boolean isArithmetic(BinaryOperator operator) {
        return switch (operator) {
            case ADD, SUB, MUL, DIV, MOD -> true;
            default -> false;
        };
    }

    private IntExpr asInteger(ArithExpr<?> expression) {
        if (!expression.getSort().equals(context.getIntSort())) {
            throw new IllegalStateException("Modulo is only supported for integers");
        }
        return (IntExpr) expression;
    }

    private Expr<?> symbol(String name, Sort sort) {
        Sort existingSort = symbolSorts.get(name);
        if (existingSort != null && !existingSort.equals(sort)) {
            throw new IllegalStateException("Symbol " + name + " is used with incompatible sorts: " + existingSort + " and " + sort);
        }
        symbolSorts.putIfAbsent(name, sort);
        return symbols.computeIfAbsent(name, ignored -> context.mkConst(name, sort));
    }

    Map<String, String> counterexample(Model model) {
        Map<String, String> values = new LinkedHashMap<>();
        symbols.forEach((name, expression) -> {
            Expr<?> value = model.evaluate(expression, false);
            if (value != null) {
                values.put(name, value.toString());
            }
        });
        return Map.copyOf(values);
    }

    private IllegalStateException unsupported(Expression expression, String reason) {
        return new IllegalStateException(reason + ": " + expression.getClass().getSimpleName());
    }
}
