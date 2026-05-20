package context;

import rj_language.ast.Expression;

/**
 * Stores refinement metadata extracted from method/constructor annotations.
 */
public class MethodRefinementContract {
    private Expression methodRefinement;
    private Expression from;
    private Expression to;
    private String msg;

    public MethodRefinementContract() {
        this.from = null;
        this.to = null;
        this.msg = null;
    }

    public Expression getMethodRefinement() {
        return methodRefinement;
    }

    public void setMethodRefinement(Expression methodRefinement) {
        this.methodRefinement = methodRefinement;
    }

    public void addStateTransition(Expression from, Expression to, String msg) {
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


    public boolean isEmpty() {
        return methodRefinement == null && from == null && to == null;
    }
}