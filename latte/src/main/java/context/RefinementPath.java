package context;

import java.util.ArrayList;
import java.util.List;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;

/**
 * A sequence of boolean expressions representing a path through a refinement tree,
 * where each expression narrows the set of valid program states at a given point.
 *
 * <p>The path accumulates refinement conditions as they are
 * traversed. It can be collapsed into a single conjunctive expression via
 * {@link #toConjunct()}.</p>
 *
 * <p>Instances are mutable; use {@link #addExpression(Expression)} to extend the path.</p>
 */
public class RefinementPath {
    public final List<Expression> path;

    public RefinementPath() {
        this.path = new ArrayList<>();
    }

    public void addExpression(Expression expression) {
        if (expression != null) {
            path.add(expression);
        }
    }

    public List<Expression> getPath() {
        return path;
    }

    /**
     * Collapses the path into a single conjunctive expression.
     *
     * @return the conjunctive expression representing the combined effect of all expressions in the path
     */
    public Expression toConjunct() {
        if (path.isEmpty()) {
            return new LiteralBoolean(true);
        }
        Expression result = path.get(0);
        for (int i = 1; i < path.size(); i++) {
            result = new BinaryExpression(result, BinaryOperator.AND, path.get(i));
        }
        return result;
    }
}
