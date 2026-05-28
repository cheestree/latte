package rj_language.smt;

import java.util.Map;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

import rj_language.ast.Expression;

public class SmtSolver {
    public EntailmentResult checkEntailment(Expression assumptions, Expression goal) {
        if (goal == null) {
            return new EntailmentResult(true, Status.UNSATISFIABLE, Map.of());
        }
        try (Context ctx = new Context()) {
            SmtEncoder encoder = new SmtEncoder(ctx);
            BoolExpr assumptionExpr = assumptions == null ? ctx.mkTrue() : encoder.toBool(assumptions);
            BoolExpr goalExpr = encoder.toBool(goal);

            Solver solver = ctx.mkSolver();
            solver.add(assumptionExpr, ctx.mkNot(goalExpr));

            Status status = solver.check();
            Map<String, String> counterexample = status == Status.SATISFIABLE
                ? encoder.counterexample(solver.getModel())
                : Map.of();
            return new EntailmentResult(status == Status.UNSATISFIABLE, status, counterexample);
        }
    }

    public record EntailmentResult(
        boolean entailed,
        Status status,
        Map<String, String> counterexample
    ) {}
}
