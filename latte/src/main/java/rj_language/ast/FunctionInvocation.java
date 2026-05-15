package rj_language.ast;

import java.util.List;
import java.util.stream.Collectors;

public class FunctionInvocation extends Expression {
    private final String name;
    private final List<Expression> arguments;

    public FunctionInvocation(String name, List<Expression> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        String args = arguments.stream()
            .map(Expression::toString)
            .collect(Collectors.joining(", "));
        return name + "(" + args + ")";
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitFunctionInvocation(this);
    }
}
