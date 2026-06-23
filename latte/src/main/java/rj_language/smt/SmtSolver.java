package rj_language.smt;

import java.util.Map;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

import rj_language.ast.Expression;

/**
 * Checks whether a refinement goal follows from the current assumptions.
 *
 * <p>The entailment {@code assumptions => goal} is checked by asking Z3 whether
 * {@code assumptions && !goal} is unsatisfiable.</p>
 */
public class SmtSolver {
    public EntailmentResult checkEntailment(Expression assumptions, Expression goal) {
        if (goal == null) {
            return new EntailmentResult(true, Status.UNSATISFIABLE, Map.of());
        }

        try (Context context = new Context()) {
            SmtEncoder encoder = new SmtEncoder(context);
            BoolExpr encodedAssumptions = assumptions == null ? context.mkTrue() : encoder.encodeBoolean(assumptions);
            BoolExpr encodedGoal = encoder.encodeBoolean(goal);

            Solver solver = context.mkSolver();
            solver.add(new BoolExpr[] {
                encodedAssumptions,
                context.mkNot(encodedGoal)
            });

            Status status = solver.check();
            Map<String, String> counterexample = status == Status.SATISFIABLE ? encoder.counterexample(solver.getModel()) : Map.of();

            return new EntailmentResult(status == Status.UNSATISFIABLE, status, counterexample);
        }
    }

    public record EntailmentResult(
        boolean entailed,
        Status status,
        Map<String, String> counterexample
    ) {}
}
