package typechecking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.BinaryExpression;
import rj_language.ast.Expression;
import rj_language.ast.FieldAccess;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.ReturnExpression;
import rj_language.ast.UnaryExpression;
import rj_language.ast.Var;
import spoon.reflect.reference.CtTypeReference;

public class Evaluator {
	private final ClassLevelMaps maps;
	private final Map<String, CtTypeReference<?>> typeEnv;
	private final SymbolicEnvironment symbEnv;
	private final PermissionEnvironment permEnv;
	private final RefinementPath refinementPath;

	public Evaluator(
		ClassLevelMaps maps,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		if (refinementPath == null) {
			throw new IllegalArgumentException("refinementPath cannot be null");
		}
		this.maps = maps;
		this.typeEnv = typeEnv;
		this.symbEnv = symbEnv;
		this.permEnv = permEnv;
		this.refinementPath = refinementPath;
	}

	public PredicateEvalResult evalPredicate(Expression predicate) {
		if (predicate == null) {
			return new PredicateEvalResult(null, symbEnv, permEnv, refinementPath);
		}
		return evalExpression(predicate);
	}

	private PredicateEvalResult evalExpression(Expression expression) {
		// T-Pred-var / T-Pred-field: first evaluate to 𝜈, then require α > shared.
		if (expression instanceof Var var) {
			return evalVar(var);
		}
		if (expression instanceof FieldAccess fieldAccess) {
			return evalFieldAccess(fieldAccess);
		}
		// Pred-const: constants, including result, preserve Δ; Σ; φ.
		if (expression instanceof LiteralBoolean
			|| expression instanceof LiteralInt
			|| expression instanceof LiteralReal
			|| expression instanceof LiteralString
			|| expression instanceof ReturnExpression) {
			return new PredicateEvalResult(expression, symbEnv, permEnv, refinementPath);
		}
		// T-Pred: recursively validate operands left-to-right and thread Δ; Σ; φ.
		if (expression instanceof UnaryExpression unaryExpression) {
			PredicateEvalResult operand = evalExpression(unaryExpression.getExpression());
			return new PredicateEvalResult(
				new UnaryExpression(unaryExpression.getOperator(), operand.predicate()),
				operand.symbEnv(),
				operand.permEnv(),
				operand.refinementPath());
		}
		if (expression instanceof BinaryExpression binaryExpression) {
			PredicateEvalResult left = evalExpression(binaryExpression.getLeft());
			PredicateEvalResult right = evalExpression(binaryExpression.getRight());
			// Milestone 3.1: predicate binary operators pass through after their
			// operands are evaluated to symbolic form. Milestone 3.4 will add
			// fresh result equalities for program-expression EvalBinary.
			return new PredicateEvalResult(
				new BinaryExpression(left.predicate(), binaryExpression.getOperator(), right.predicate()),
				right.symbEnv(),
				right.permEnv(),
				right.refinementPath());
		}
		if (expression instanceof FunctionInvocation invocation) {
			return evalFunctionInvocation(invocation);
		}

		throw new IllegalStateException("Unsupported predicate expression: " + expression.getClass().getSimpleName());
	}

	private PredicateEvalResult evalVar(Var var) {
		// T-Pred-var premise 1: EvalVar gives Δ(x)=𝜈 and Σ(𝜈) ≠ ⊥.
		String name = var.getName();
		SymbolicValue value = requireSymbolicValue(name, symbEnv);
		UniquenessAnnotation perm = requirePermission(value, permEnv, "variable", name);
		// T-Pred-var premises 2-3: Σ ⊢ 𝜈 : α ⊣ Σ′ and α > shared.
		ensurePredicatePermission(perm, "variable", name);
		return new PredicateEvalResult(new Var(value.toString()), symbEnv, permEnv, refinementPath);
	}

	private PredicateEvalResult evalFieldAccess(FieldAccess fieldAccess) {
		// T-Pred-field premise 1: evaluate x.f ⇓ 𝜈 through EvalField.
		Expression receiverExpr = fieldAccess.getReceiver();
		if (!(receiverExpr instanceof Var receiverVar)) {
			throw new IllegalStateException("Only variable receivers are supported in predicates: " + receiverExpr);
		}

		String receiverName = receiverVar.getName();
		SymbolicValue receiverValue = requireSymbolicValue(receiverName, symbEnv);
		UniquenessAnnotation receiverPerm = requirePermission(receiverValue, permEnv, "receiver", receiverName);
		if (receiverPerm.isBottom()) {
			throw new IllegalStateException("Receiver is inaccessible in predicate: " + receiverName);
		}

		String fieldName = fieldAccess.getField();
		SymbolicValue fieldValue = symbEnv.get(receiverValue, fieldName);
		if (fieldValue == null) {
			CtTypeReference<?> receiverType = typeEnv != null ? typeEnv.get(receiverName) : null;
			if (receiverType == null) {
				throw new IllegalStateException("Missing type for receiver " + receiverName + " when evaluating " + receiverName + "." + fieldName);
			}
			UniquenessAnnotation fieldPerm = maps.getFieldAnnotation(fieldName, receiverType);
			if (fieldPerm == null) {
				throw new IllegalStateException("Unknown field " + fieldName + " on type " + receiverType);
			}
			if (receiverPerm.isBottom()) {
				throw new IllegalStateException("Receiver is inaccessible in predicate: " + receiverName);
			}

			if (!fieldPerm.isShared() && !receiverPerm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
				throw new IllegalStateException("Receiver " + receiverName + " with permission " + receiverPerm + " cannot access non-shared field " + fieldName);
			}
			fieldValue = symbEnv.addField(receiverValue, fieldName);
			permEnv.add(fieldValue, fieldPerm);
		}

		UniquenessAnnotation fieldPerm = requirePermission(fieldValue, permEnv, "field", receiverName + "." + fieldName);
		// T-Pred-field premises 2-3: Σ ⊢ 𝜈 : α ⊣ Σ′ and α > shared.
		ensurePredicatePermission(fieldPerm, "field", receiverName + "." + fieldName);

		return new PredicateEvalResult(new Var(fieldValue.toString()), symbEnv, permEnv, refinementPath);
	}

	private PredicateEvalResult evalFunctionInvocation(FunctionInvocation invocation) {
		List<Expression> newArgs = new ArrayList<>();

		for (Expression arg : invocation.getArguments()) {
			PredicateEvalResult argResult = evalExpression(arg);
			newArgs.add(argResult.predicate());
		}

		return new PredicateEvalResult(
			new FunctionInvocation(invocation.getName(), newArgs),
			symbEnv,
			permEnv,
			refinementPath);
	}

	private SymbolicValue requireSymbolicValue(String name, SymbolicEnvironment symbEnv) {
		SymbolicValue value = symbEnv.get(name);
		if (value == null) {
			throw new IllegalStateException("Unknown symbolic value for variable " + name);
		}
		return value;
	}

	private UniquenessAnnotation requirePermission(
		SymbolicValue value,
		PermissionEnvironment permEnv,
		String kind,
		String name) {
		UniquenessAnnotation perm = permEnv.get(value);
		if (perm == null) {
			throw new IllegalStateException("Missing permission for " + kind + " " + name);
		}
		return perm;
	}

	private void ensurePredicatePermission(UniquenessAnnotation perm, String kind, String name) {
		if (!perm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Predicate requires α > shared but found α=" + perm + " for " + kind + " " + name);
		}
	}

	public static record PredicateEvalResult(
		Expression predicate,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath){}
}
