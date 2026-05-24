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

	public Evaluator(ClassLevelMaps maps) {
		this.maps = maps;
	}

	public PredicateEvalResult evalPredicate(
		Expression predicate,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		if (predicate == null) {
			return new PredicateEvalResult(null, symbEnv, permEnv, refinementPath);
		}
		return evalExpression(predicate, typeEnv, symbEnv, permEnv, refinementPath);
	}

	private PredicateEvalResult evalExpression(
		Expression expression,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		if (expression instanceof Var var) {
			return evalVar(var, symbEnv, permEnv, refinementPath);
		}
		if (expression instanceof FieldAccess fieldAccess) {
			return evalFieldAccess(fieldAccess, typeEnv, symbEnv, permEnv, refinementPath);
		}
		if (expression instanceof LiteralBoolean
			|| expression instanceof LiteralInt
			|| expression instanceof LiteralReal
			|| expression instanceof LiteralString
			|| expression instanceof ReturnExpression) {
			return new PredicateEvalResult(expression, symbEnv, permEnv, refinementPath);
		}
		if (expression instanceof UnaryExpression unaryExpression) {
			PredicateEvalResult operand = evalExpression(
				unaryExpression.getExpression(), typeEnv, symbEnv, permEnv, refinementPath);
			return new PredicateEvalResult(
				new UnaryExpression(unaryExpression.getOperator(), operand.getPredicate()),
				operand.getSymbEnv(),
				operand.getPermEnv(),
				operand.getRefinementPath());
		}
		if (expression instanceof BinaryExpression binaryExpression) {
			PredicateEvalResult left = evalExpression(
				binaryExpression.getLeft(), typeEnv, symbEnv, permEnv, refinementPath);
			PredicateEvalResult right = evalExpression(
				binaryExpression.getRight(), typeEnv, left.getSymbEnv(), left.getPermEnv(), left.getRefinementPath());
			return new PredicateEvalResult(
				new BinaryExpression(left.getPredicate(), binaryExpression.getOperator(), right.getPredicate()),
				right.getSymbEnv(),
				right.getPermEnv(),
				right.getRefinementPath());
		}
		if (expression instanceof FunctionInvocation invocation) {
			return evalFunctionInvocation(invocation, typeEnv, symbEnv, permEnv, refinementPath);
		}

		throw new IllegalStateException("Unsupported predicate expression: " + expression.getClass().getSimpleName());
	}

	private PredicateEvalResult evalVar(
		Var var,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		String name = var.getName();
		SymbolicValue value = requireSymbolicValue(name, symbEnv);
		UniquenessAnnotation perm = requirePermission(value, permEnv, "variable", name);
		ensureNonShared(perm, "variable", name);
		if (!perm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Permission check failed for variable " + name + " with " + perm);
		}
		return new PredicateEvalResult(new Var(value.toString()), symbEnv, permEnv, refinementPath);
	}

	private PredicateEvalResult evalFieldAccess(
		FieldAccess fieldAccess,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
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

			if (receiverPerm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
				fieldValue = symbEnv.addField(receiverValue, fieldName);
				permEnv.add(fieldValue, fieldPerm);
			} else if (receiverPerm.isGreaterEqualThan(Uniqueness.SHARED)) {
				if (!fieldPerm.isShared()) {
					throw new IllegalStateException("Shared receiver " + receiverName + " cannot access non-shared field " + fieldName);
				}
				fieldValue = symbEnv.addField(receiverValue, fieldName);
				permEnv.add(fieldValue, fieldPerm);
			} else {
				throw new IllegalStateException("Receiver permission is too weak for field access: " + receiverPerm);
			}
		}

		UniquenessAnnotation fieldPerm = requirePermission(fieldValue, permEnv, "field", receiverName + "." + fieldName);
		ensureNonShared(fieldPerm, "field", receiverName + "." + fieldName);
		if (!permEnv.usePermissionAs(fieldValue, fieldPerm, fieldPerm)) {
			throw new IllegalStateException("Permission check failed for field " + receiverName + "." + fieldName + " with " + fieldPerm);
		}

		return new PredicateEvalResult(new Var(fieldValue.toString()), symbEnv, permEnv, refinementPath);
	}

	private PredicateEvalResult evalFunctionInvocation(
		FunctionInvocation invocation,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		List<Expression> newArgs = new ArrayList<>();
		SymbolicEnvironment currentSymb = symbEnv;
		PermissionEnvironment currentPerm = permEnv;
		RefinementPath currentPath = refinementPath;

		for (Expression arg : invocation.getArguments()) {
			PredicateEvalResult argResult = evalExpression(arg, typeEnv, currentSymb, currentPerm, currentPath);
			newArgs.add(argResult.getPredicate());
			currentSymb = argResult.getSymbEnv();
			currentPerm = argResult.getPermEnv();
			currentPath = argResult.getRefinementPath();
		}

		return new PredicateEvalResult(
			new FunctionInvocation(invocation.getName(), newArgs),
			currentSymb,
			currentPerm,
			currentPath);
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

	private void ensureNonShared(UniquenessAnnotation perm, String kind, String name) {
		if (!perm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Predicate uses shared " + kind + " " + name + " with " + perm);
		}
	}

	public static final class PredicateEvalResult {
		private final Expression predicate;
		private final SymbolicEnvironment symbEnv;
		private final PermissionEnvironment permEnv;
		private final RefinementPath refinementPath;

		public PredicateEvalResult(
			Expression predicate,
			SymbolicEnvironment symbEnv,
			PermissionEnvironment permEnv,
			RefinementPath refinementPath) {
			this.predicate = predicate;
			this.symbEnv = symbEnv;
			this.permEnv = permEnv;
			this.refinementPath = refinementPath;
		}

		public Expression getPredicate() {
			return predicate;
		}

		public SymbolicEnvironment getSymbEnv() {
			return symbEnv;
		}

		public PermissionEnvironment getPermEnv() {
			return permEnv;
		}

		public RefinementPath getRefinementPath() {
			return refinementPath;
		}
	}
}
