package context;

import rj_language.ast.Expression;

/**
 * Stores state transition refinement metadata extracted from method/constructor annotations.
 */
public class RefinementContract {
    private final Expression from;
    private final Expression to;
    private final String msg;

    public RefinementContract(Expression from, Expression to, String msg) {
        this.from = from;
        this.to = to;
        this.msg = msg;
    }

    public Expression getFrom() {
        return from;
    }

    public Expression getTo() {
        return to;
    }

    public String getMsg() {
        return msg;
    }
}