package context;

import rj_language.ast.Expression;

/**
 * Stores refinement metadata extracted from method/constructor annotations.
 */
public class MethodRefinementContract {
    private Expression methodRefinement;
    private StateTransition stateTransition;

    public MethodRefinementContract() {
        this.stateTransition = null;
    }

    public Expression getMethodRefinement() {
        return methodRefinement;
    }

    public void setMethodRefinement(Expression methodRefinement) {
        this.methodRefinement = methodRefinement;
    }

    public void addStateTransition(Expression from, Expression to, String msg) {
        this.stateTransition = new StateTransition(from, to, msg);
    }

    public StateTransition getStateTransition() {
        return stateTransition;
    }


    public boolean isEmpty() {
        return methodRefinement == null && stateTransition == null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class StateTransition {
        private final Expression from;
        private final Expression to;
        private final String msg;

        public StateTransition(Expression from, Expression to, String msg) {
            this.from = from;
            this.to = to;
            this.msg = normalize(msg);
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
}