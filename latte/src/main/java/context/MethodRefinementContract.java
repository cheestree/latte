package context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import rj_language.ast.Expression;
import rj_language.visitors.ExpressionPrettyPrinter;

/**
 * Stores refinement metadata extracted from method/constructor annotations.
 */
public class MethodRefinementContract {
    private Expression methodRefinement;
    private final Map<String, Expression> parameterRefinements;
    private final List<StateTransition> stateTransitions;

    public MethodRefinementContract() {
        this.parameterRefinements = new LinkedHashMap<>();
        this.stateTransitions = new ArrayList<>();
    }

    public Expression getMethodRefinement() {
        return methodRefinement;
    }

    public void setMethodRefinement(Expression methodRefinement) {
        this.methodRefinement = methodRefinement;
    }

    public void addParameterRefinement(String parameterName, Expression predicate) {
        if (parameterName != null && !parameterName.isBlank() && predicate != null) {
            parameterRefinements.put(parameterName, predicate);
        }
    }

    public Expression getParameterRefinement(String parameterName) {
        return parameterRefinements.get(parameterName);
    }

    public Map<String, Expression> getParameterRefinements() {
        return Collections.unmodifiableMap(parameterRefinements);
    }

    public void addStateTransition(Expression from, Expression to, String msg) {
        stateTransitions.add(new StateTransition(from, to, msg));
    }

    public List<StateTransition> getStateTransitions() {
        return Collections.unmodifiableList(stateTransitions);
    }

    /**
     * Combined precondition equivalent to conjunction of all non-empty from predicates.
     */
    public String getCombinedPrecondition() {
        StringJoiner sj = new StringJoiner(" && ");
        for (StateTransition t : stateTransitions) {
            String rendered = ExpressionPrettyPrinter.print(t.getFrom());
            if (rendered != null) {
                sj.add(rendered);
            }
        }
        return sj.length() == 0 ? null : sj.toString();
    }

    /**
     * Combined postcondition equivalent to conjunction of all non-empty to predicates.
     */
    public String getCombinedPostcondition() {
        StringJoiner sj = new StringJoiner(" && ");
        for (StateTransition t : stateTransitions) {
            String rendered = ExpressionPrettyPrinter.print(t.getTo());
            if (rendered != null) {
                sj.add(rendered);
            }
        }
        return sj.length() == 0 ? null : sj.toString();
    }

    public boolean isEmpty() {
        return methodRefinement == null && parameterRefinements.isEmpty() && stateTransitions.isEmpty();
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