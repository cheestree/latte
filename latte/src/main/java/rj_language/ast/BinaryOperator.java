package rj_language.ast;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum BinaryOperator {
    MUL("*"), DIV("/"), MOD("%"),
    ADD("+"), SUB("-"),
    LT("<"),  GT(">"), LE("<="), GE(">="),
    EQ("=="), NEQ("!="),
    AND("&&"), OR("||");

    public final String symbol;
    BinaryOperator(String symbol) { this.symbol = symbol; }

    private static final Map<String, BinaryOperator> BY_SYMBOL =
        Arrays.stream(values()).collect(Collectors.toMap(op -> op.symbol, op -> op));

    public static BinaryOperator fromSymbol(String symbol) {
        return Optional.ofNullable(BY_SYMBOL.get(symbol))
            .orElseThrow(() -> new IllegalStateException("Unsupported operator: " + symbol));
    }
}
