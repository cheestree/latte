package context;

import java.util.List;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;

public class RefinementPath {
    public List<Expression> path;

    public RefinementPath() {
        this.path = List.of();
    }

    public RefinementPath(List<Expression> path) {
        this.path = List.copyOf(path);
    }

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
