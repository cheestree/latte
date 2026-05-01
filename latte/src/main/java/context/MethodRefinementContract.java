package context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Stores refinement metadata extracted from method/constructor annotations.
 */
public class MethodRefinementContract {
    private String methodRefinement;
    private final Map<String, String> parameterRefinements;
    private final List<StateTransition> stateTransitions;

    public MethodRefinementContract() {
        this.parameterRefinements = new LinkedHashMap<>();
        this.stateTransitions = new ArrayList<>();
    }

    public String getMethodRefinement() {
        return methodRefinement;
    }

    public void setMethodRefinement(String methodRefinement) {
        this.methodRefinement = normalize(methodRefinement);
    }

    public void addParameterRefinement(String parameterName, String predicate) {
        String normalized = normalize(predicate);
        if (parameterName != null && !parameterName.isBlank() && normalized != null) {
            parameterRefinements.put(parameterName, normalized);
        }
    }

    public String getParameterRefinement(String parameterName) {
        return parameterRefinements.get(parameterName);
    }

    public Map<String, String> getParameterRefinements() {
        return Collections.unmodifiableMap(parameterRefinements);
    }

    public void addStateTransition(String from, String to, String msg) {
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
            if (t.getFrom() != null) {
                sj.add("(" + t.getFrom() + ")");
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
            if (t.getTo() != null) {
                sj.add("(" + t.getTo() + ")");
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
        private final String from;
        private final String to;
        private final String msg;

        public StateTransition(String from, String to, String msg) {
            this.from = normalize(from);
            this.to = normalize(to);
            this.msg = normalize(msg);
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public String getMsg() {
            return msg;
        }
    }
}