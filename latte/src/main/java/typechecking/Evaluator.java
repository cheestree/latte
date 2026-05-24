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
		// T-Pred-var / T-Pred-field dispatch: variables and fields are first
		// evaluated to symbolic values, then checked against alpha > shared.
		if (expression instanceof Var var) {
			return evalVar(var, symbEnv, permEnv, refinementPath);
		}
		if (expression instanceof FieldAccess fieldAccess) {
			return evalFieldAccess(fieldAccess, typeEnv, symbEnv, permEnv, refinementPath);
		}
		// Pred-const: constants, including result, do not update Delta/Sigma/phi.
		if (expression instanceof LiteralBoolean
			|| expression instanceof LiteralInt
			|| expression instanceof LiteralReal
			|| expression instanceof LiteralString
			|| expression instanceof ReturnExpression) {
			return new PredicateEvalResult(expression, symbEnv, permEnv, refinementPath);
		}
		// T-Pred: recursively validate operands left-to-right and thread
		// Delta/Sigma/phi through the sub-derivations.
		if (expression instanceof UnaryExpression unaryExpression) {
			PredicateEvalResult operand = evalExpression(
				unaryExpression.getExpression(), typeEnv, symbEnv, permEnv, refinementPath);
			return new PredicateEvalResult(
				new UnaryExpression(unaryExpression.getOperator(), operand.predicate()),
				operand.symbEnv(),
				operand.permEnv(),
				operand.refinementPath());
		}
		if (expression instanceof BinaryExpression binaryExpression) {
			PredicateEvalResult left = evalExpression(
				binaryExpression.getLeft(), typeEnv, symbEnv, permEnv, refinementPath);
			PredicateEvalResult right = evalExpression(
				binaryExpression.getRight(), typeEnv, left.symbEnv(), left.permEnv(), left.refinementPath());
			return new PredicateEvalResult(
				new BinaryExpression(left.predicate(), binaryExpression.getOperator(), right.predicate()),
				right.symbEnv(),
				right.permEnv(),
				right.refinementPath());
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
		// T-Pred-var premise 1: EvalVar gives Delta(x) = nu and Sigma(nu) != bottom.
		String name = var.getName();
		SymbolicValue value = requireSymbolicValue(name, symbEnv);
		UniquenessAnnotation perm = requirePermission(value, permEnv, "variable", name);
		// T-Pred-var premises 2-3: use the value only if alpha > shared.
		ensurePredicatePermission(perm, "variable", name);
		return new PredicateEvalResult(new Var(value.toString()), symbEnv, permEnv, refinementPath);
	}

	private PredicateEvalResult evalFieldAccess(
		FieldAccess fieldAccess,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		// T-Pred-field premise 1: evaluate x.f through the same field cases as EvalField.
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
		// T-Pred-field premises 2-3: the resulting field value must have alpha > shared.
		ensurePredicatePermission(fieldPerm, "field", receiverName + "." + fieldName);

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
			newArgs.add(argResult.predicate());
			currentSymb = argResult.symbEnv();
			currentPerm = argResult.permEnv();
			currentPath = argResult.refinementPath();
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

	private void ensurePredicatePermission(UniquenessAnnotation perm, String kind, String name) {
		if (perm.isBottom()) {
			throw new IllegalStateException("Predicate uses inaccessible " + kind + " " + name + " with " + perm);
		}
		if (!perm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Predicate uses shared " + kind + " " + name + " with " + perm);
		}
	}

	public static record PredicateEvalResult(
		Expression predicate,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath){}
}
