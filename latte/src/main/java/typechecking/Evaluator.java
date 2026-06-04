package typechecking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
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

	public Evaluator(
		ClassLevelMaps maps,
		Map<String, CtTypeReference<?>> typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv) {
		this.maps = maps;
		this.typeEnv = typeEnv;
		this.symbEnv = symbEnv;
		this.permEnv = permEnv;
	}

	public Expression evalPredicate(Expression predicate) {
		if (predicate == null) {
			return null;
		}
		return evalExpression(predicate);
	}

	private Expression evalExpression(Expression expression) {
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
			return expression;
		}
		// T-Pred: recursively validate operands left-to-right, mutating Δ; Σ in place.
		if (expression instanceof UnaryExpression unaryExpression) {
			Expression operand = evalExpression(unaryExpression.getExpression());
			return new UnaryExpression(unaryExpression.getOperator(), operand);
		}
		if (expression instanceof BinaryExpression binaryExpression) {
			Expression left = evalExpression(binaryExpression.getLeft());
			Expression right = evalExpression(binaryExpression.getRight());
			// Milestone 3.1: predicate binary operators pass through after their
			// operands are evaluated to symbolic form. Milestone 3.4 will add
			// fresh result equalities for program-expression EvalBinary.
			return new BinaryExpression(left, binaryExpression.getOperator(), right);
		}
		if (expression instanceof FunctionInvocation invocation) {
			return evalFunctionInvocation(invocation);
		}

		throw new IllegalStateException("Unsupported predicate expression: " + expression.getClass().getSimpleName());
	}

	private Expression evalVar(Var var) {
		// T-Pred-var premise 1: EvalVar gives Δ(x)=𝜈 and Σ(𝜈) ≠ ⊥.
		String name = var.getName();
		SymbolicValue value = symbEnv.get(name);
		if (value == null) {
			throw new IllegalStateException("Unknown symbolic value for variable " + name);
		}
		UniquenessAnnotation perm = permEnv.get(value);
		if (perm == null) {
			throw new IllegalStateException("Missing permission for variable " + name);
		}
		// T-Pred-var premises 2-3: Σ ⊢ 𝜈 : α ⊣ Σ′ and α > shared.
		if (!perm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Predicate requires α > shared but found α=" + perm + " for variable " + name);
		}
		return new Var(value.toString());
	}

	private Expression evalFieldAccess(FieldAccess fieldAccess) {
		// T-Pred-field premise 1: evaluate x.f ⇓ 𝜈 through EvalField.
		Expression receiverExpr = fieldAccess.getReceiver();
		if (!(receiverExpr instanceof Var receiverVar)) {
			throw new IllegalStateException("Only variable receivers are supported in predicates: " + receiverExpr);
		}

		String receiverName = receiverVar.getName();
		SymbolicValue receiverValue = symbEnv.get(receiverName);
		if (receiverValue == null) {
			throw new IllegalStateException("Unknown symbolic value for variable " + receiverName);
		}
		UniquenessAnnotation receiverPerm = permEnv.get(receiverValue);
		if (receiverPerm == null) {
			throw new IllegalStateException("Missing permission for receiver " + receiverName);
		}
		if (receiverPerm.isBottom()) {
			throw new IllegalStateException("Receiver is inaccessible in predicate: " + receiverName);
		}

		String fieldName = fieldAccess.getField();
		CtTypeReference<?> receiverType = typeEnv != null ? typeEnv.get(receiverName) : null;
		if (receiverType == null) {
			throw new IllegalStateException("Missing type for receiver " + receiverName + " when evaluating " + receiverName + "." + fieldName);
		}
		UniquenessAnnotation declaredFieldPerm = maps.getFieldAnnotation(fieldName, receiverType);
		if (declaredFieldPerm == null) {
			throw new IllegalStateException("Unknown field " + fieldName + " on type " + receiverType);
		}
		if (!declaredFieldPerm.isShared() && !receiverPerm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Receiver " + receiverName + " with permission " + receiverPerm + " cannot access non-shared field " + fieldName);
		}

		SymbolicValue fieldValue = symbEnv.get(receiverValue, fieldName);
		if (fieldValue == null) {
			fieldValue = symbEnv.addField(receiverValue, fieldName);
			permEnv.add(fieldValue, declaredFieldPerm);
		}

		UniquenessAnnotation fieldPerm = permEnv.get(fieldValue);
		if (fieldPerm == null) {
			throw new IllegalStateException("Missing permission for field " + receiverName + "." + fieldName);
		}
		// T-Pred-field premises 2-3: Σ ⊢ 𝜈 : α ⊣ Σ′ and α > shared.
		if (!fieldPerm.isGreaterEqualThan(Uniqueness.UNIQUE)) {
			throw new IllegalStateException("Predicate requires α > shared but found α=" + fieldPerm + " for field " + receiverName + "." + fieldName);
		}

		return new Var(fieldValue.toString());
	}

	private Expression evalFunctionInvocation(FunctionInvocation invocation) {
		List<Expression> newArgs = new ArrayList<>();

		for (Expression arg : invocation.getArguments()) {
			newArgs.add(evalExpression(arg));
		}

		return new FunctionInvocation(invocation.getName(), newArgs);
	}
}
