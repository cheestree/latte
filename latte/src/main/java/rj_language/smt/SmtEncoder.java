package rj_language.smt;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Sort;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.ReturnExpression;
import rj_language.ast.UnaryExpression;
import rj_language.ast.UnaryOperator;
import rj_language.ast.Var;

public class SmtEncoder {
    private final Context ctx;
    private final Map<String, Sort> symbolSorts = new HashMap<>();
    private final Map<String, Expr<?>> symbols = new HashMap<>();
    private final Map<FunctionSignature, FuncDecl<?>> functions = new HashMap<>();

    public SmtEncoder(Context ctx) {
        this.ctx = ctx;
    }

    public BoolExpr toBool(Expression expression) {
        if (expression instanceof LiteralBoolean literal) {
            return ctx.mkBool(literal.getValue());
        }
        if (expression instanceof Var var) {
            return (BoolExpr) symbol(var.getName(), ctx.getBoolSort());
        }
        if (expression instanceof UnaryExpression unary) {
            if (unary.getOperator() != UnaryOperator.NOT) {
                throw new IllegalStateException("Unsupported unary operator for boolean: " + unary.getOperator());
            }
            return ctx.mkNot(toBool(unary.getExpression()));
        }
        if (expression instanceof BinaryExpression binary) {
            BinaryOperator op = binary.getOperator();
            switch (op) {
                case AND -> {
                    return ctx.mkAnd(toBool(binary.getLeft()), toBool(binary.getRight()));
                }
                case OR -> {
                    return ctx.mkOr(toBool(binary.getLeft()), toBool(binary.getRight()));
                }
                case EQ, NEQ -> {
                    BoolExpr eq;
                    if (isBoolLike(binary.getLeft()) || isBoolLike(binary.getRight())) {
                        eq = ctx.mkEq(toBool(binary.getLeft()), toBool(binary.getRight()));
                    } else {
                        ArithExpr<?> left = toArith(binary.getLeft());
                        ArithExpr<?> right = toArith(binary.getRight());
                        eq = ctx.mkEq(left, right);
                    }
                    return op == BinaryOperator.EQ ? eq : ctx.mkNot(eq);
                }
                case LT, LE, GT, GE -> {
                    ArithExpr<?> left = toArith(binary.getLeft());
                    ArithExpr<?> right = toArith(binary.getRight());
                    return switch (op) {
                        case LT -> ctx.mkLt(left, right);
                        case LE -> ctx.mkLe(left, right);
                        case GT -> ctx.mkGt(left, right);
                        case GE -> ctx.mkGe(left, right);
                        default -> throw new IllegalStateException("Unexpected relation operator: " + op);
                    };
                }
                default -> throw new IllegalStateException("Unsupported boolean operator: " + op);
            }
        }
        if (expression instanceof FunctionInvocation invocation) {
            return (BoolExpr) encodeFunctionInvocation(invocation, ctx.getBoolSort());
        }
        if (expression instanceof ReturnExpression) {
            // TODO: Return expressions will be supported once return substitution is implemented.
            throw new IllegalStateException("Return expression is not supported in SMT encoding");
        }
        if (expression instanceof LiteralString) {
            throw new IllegalStateException("String refinements are not supported in SMT encoding yet");
        }
        if (expression instanceof LiteralInt || expression instanceof LiteralReal) {
            throw new IllegalStateException("Non-boolean literal is not supported in SMT boolean context");
        }
        throw new IllegalStateException("Unsupported boolean expression: " + expression.getClass().getSimpleName());
    }

    private ArithExpr<?> toArith(Expression expression) {
        if (expression instanceof LiteralInt literal) {
            return ctx.mkInt(literal.getValue());
        }
        if (expression instanceof LiteralReal literal) {
            return ctx.mkReal(Double.toString(literal.getValue()));
        }
        if (expression instanceof Var var) {
            return (ArithExpr<?>) symbol(var.getName(), ctx.getIntSort());
        }
        if (expression instanceof UnaryExpression unary) {
            if (unary.getOperator() != UnaryOperator.NEGATE) {
                throw new IllegalStateException("Unsupported unary operator for arithmetic: " + unary.getOperator());
            }
            return ctx.mkUnaryMinus(toArith(unary.getExpression()));
        }
        if (expression instanceof BinaryExpression binary) {
            BinaryOperator op = binary.getOperator();
            ArithExpr<?> left = toArith(binary.getLeft());
            ArithExpr<?> right = toArith(binary.getRight());
            return switch (op) {
                case ADD -> ctx.mkAdd(left, right);
                case SUB -> ctx.mkSub(left, right);
                case MUL -> ctx.mkMul(left, right);
                case DIV -> ctx.mkDiv(left, right);
                case MOD -> ctx.mkMod(toIntArith(binary.getLeft()), toIntArith(binary.getRight()));
                default -> throw new IllegalStateException("Unsupported arithmetic operator: " + op);
            };
        }
        if (expression instanceof FunctionInvocation invocation) {
            Expr<?> result = encodeFunctionInvocation(invocation, ctx.getIntSort());
            return (ArithExpr<?>) result;
        }
        if (expression instanceof LiteralString) {
            throw new IllegalStateException("String refinements are not supported in SMT encoding yet");
        }
        if (expression instanceof LiteralBoolean || expression instanceof ReturnExpression) {
            // TODO: Return expressions will be supported once return substitution is implemented.
            throw new IllegalStateException("Non-arithmetic expression is not supported in SMT arithmetic context");
        }
        throw new IllegalStateException("Unsupported arithmetic expression: " + expression.getClass().getSimpleName());
    }

    private IntExpr toIntArith(Expression expression) {
        ArithExpr<?> value = toArith(expression);
        if (!value.getSort().equals(ctx.getIntSort())) {
            throw new IllegalStateException("Modulo is only supported for integer arithmetic");
        }
        return (IntExpr) value;
    }

    private Expr<?> symbol(String name, Sort sort) {
        Sort existingSort = symbolSorts.get(name);
        if (existingSort != null && !existingSort.equals(sort)) {
            throw new IllegalStateException("Symbol sort mismatch for " + name + ": " + existingSort + " vs " + sort);
        }
        symbolSorts.putIfAbsent(name, sort);
        return symbols.computeIfAbsent(name, n -> ctx.mkConst(n, sort));
    }

    private Expr<?> encodeFunctionInvocation(FunctionInvocation invocation, Sort returnSort) {
        List<Expression> args = invocation.getArguments();
        Sort[] argSorts = new Sort[args.size()];
        Expr<?>[] argExprs = new Expr[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Expression arg = args.get(i);
            if (isBoolLike(arg)) {
                argExprs[i] = toBool(arg);
            } else {
                argExprs[i] = toArith(arg);
            }             
            argSorts[i] = argExprs[i].getSort();
        }

        FunctionSignature signature = new FunctionSignature(invocation.getName(), returnSort, argSorts);
        FuncDecl<?> decl = functions.computeIfAbsent(signature, sig -> ctx.mkFuncDecl(sig.getName(), sig.getArgSorts(), sig.getReturnSort()));
        return decl.apply(argExprs);
    }

    private boolean isBoolLike(Expression expression) {
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

    public Map<String, String> counterexample(Model model) {
        Map<String, String> values = new LinkedHashMap<>();
        symbols.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                Expr<?> value = model.evaluate(entry.getValue(), false);
                if (value != null) {
                    values.put(entry.getKey(), value.toString());
                }
            });
        return Map.copyOf(values);
    }
}
