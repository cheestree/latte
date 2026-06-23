package context;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import spoon.reflect.reference.CtTypeReference;

/**
 * Type Environment class to store the variables to their types
 * Γ ::= ∅ | 𝑥: 𝐶, Γ
 */
public class TypeEnvironment {
    private LinkedList<Map<Variable, CtTypeReference<?>>> typeEnv;

    public TypeEnvironment() {
        typeEnv = new LinkedList<>();
    }

    /**
     * Add a variable type to the current scope.
     * @param name the variable name
     * @param type the type of the variable
     */
    public void add(String name, CtTypeReference<?> type) {
        if (typeEnv.isEmpty()) {
            enterScope();
        }
        typeEnv.getFirst().put(new Variable(name), type);
    }

    /**
     * Get the type of a variable from the environment
     * @param var the variable to look up
     * @return the type of the variable, or null if not found
     */
    public CtTypeReference<?> get(String name) {
        return get(new Variable(name));
    }

    /**
     * Get the type of a variable from the environment
     * @param var the variable to look up
     * @return the type of the variable, or null if not found
     */
    CtTypeReference<?> get(Variable var) {
        for (Map<Variable, CtTypeReference<?>> map : typeEnv) {
            if (map.containsKey(var)) {
                return map.get(var);
            }
        }
        return null;
    }

    /**
     * Check if a variable is bound in any scope.
     * @param name the variable name
     * @return true if the variable has a type binding
     */
    public boolean contains(String name) {
        return get(name) != null;
    }

    /**
     * Enter a new scope.
     */
    public void enterScope() {
        typeEnv.addFirst(new HashMap<>());
    }

    /**
     * Exit the current scope.
     */
    public void exitScope() {
        typeEnv.removeFirst();
    }

    @Override
    public String toString() {
        return typeEnv.toString();
    }
}
