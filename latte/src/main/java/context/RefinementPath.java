package context;

import java.util.ArrayList;
import java.util.List;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;

public class RefinementPath {
    List<Expression> path;

    public RefinementPath() {
        this.path = List.of();
    }

    public RefinementPath(List<Expression> path) {
        this.path = List.copyOf(path);
    }

    public RefinementPath addExpression(Expression contract) {
        List<Expression> newPath = new ArrayList<>(path);
        newPath.add(contract);
        return new RefinementPath(newPath);
    }

    public BinaryExpression toConjunct() {
        if (path.isEmpty()) {
            return new BinaryExpression(new LiteralBoolean(true), BinaryOperator.AND, new LiteralBoolean(true));
        }
        Expression result = path.get(0);
        for (int i = 1; i < path.size(); i++) {
            result = new BinaryExpression(result, BinaryOperator.AND, path.get(i));
        }
        return new BinaryExpression(new LiteralBoolean(true), BinaryOperator.AND, result);
    }
}
