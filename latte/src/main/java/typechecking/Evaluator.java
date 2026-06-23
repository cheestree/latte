package typechecking;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.FieldAccess;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.UnaryExpression;
import rj_language.ast.Var;
import spoon.reflect.reference.CtTypeReference;

public class Evaluator {
	private final ClassLevelMaps maps;
	private final TypeEnvironment typeEnv;
	private final SymbolicEnvironment symbEnv;
	private final PermissionEnvironment permEnv;
	private final RefinementPath refinementPath;

	public Evaluator(
		ClassLevelMaps maps,
		TypeEnvironment typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		RefinementPath refinementPath) {
		this.maps = maps;
		this.typeEnv = typeEnv;
		this.symbEnv = symbEnv;
		this.permEnv = permEnv;
		this.refinementPath = refinementPath;
	}

	public Expression eval(Expression predicate) {
		if (predicate == null) {
			return null;
		}
		SymbolicValue value = evalExpression(predicate);
    	return new Var(value.toString());
	}

	/**
	 * Flattens a predicate expression by evaluating variables and field accesses to their symbolic values, and replacing constants and operations with fresh symbolic values and corresponding path conditions.
	 * @param expression the predicate expression to evaluate
	 * @return the evaluated expression, with variables and field accesses replaced by symbolic values
	 */
	private SymbolicValue evalExpression(Expression expression) {
		if (expression instanceof Var var) {
			return evalVarValue(var);
		}
		if (expression instanceof FieldAccess fieldAccess) {
			return evalFieldValue(fieldAccess);
		}
		if (expression instanceof LiteralBoolean
			|| expression instanceof LiteralInt
			|| expression instanceof LiteralReal
			|| expression instanceof LiteralString) {
			return evalConstValue(expression);
		}
		if (expression instanceof UnaryExpression unaryExpression) {
			return evalUnaryValue(unaryExpression);
		}
		if (expression instanceof BinaryExpression binaryExpression) {
			return evalBinaryValue(binaryExpression);
		}

		throw new IllegalStateException("Unsupported evaluation expression: " + expression.getClass().getSimpleName());
	}

	/**
	 *  EvalVar
	 *	Δ(𝑥) = 𝜈 Σ(𝜈) ≠ ⊥
	 *  ----------------------------------
	 *	Γ; Δ; Σ; 𝜑 ⊢ 𝑥 ⇓ 𝜈 ⊣ Γ; Δ; Σ; 𝜑
	 */
	private SymbolicValue evalVarValue(Var var) {
		// Δ(𝑥) = 𝜈
		SymbolicValue value = symbEnv.getOrThrow(var.getName());
		// Σ(𝜈) ≠ ⊥
		UniquenessAnnotation perm = permEnv.getOrThrow(value, "variable " + var.getName());
		if (perm.isBottom()) {
			throw new IllegalStateException("Variable is inaccessible in evaluation: " + var.getName());
		}
		return value;
	}

	private SymbolicValue evalFieldValue(FieldAccess fieldAccess) {
		Expression receiverExpr = fieldAccess.getReceiver();
		if (!(receiverExpr instanceof Var receiverVar)) {
			throw new IllegalStateException("Only variable receivers are supported in evaluation: " + receiverExpr);
		}

		// Δ(𝑥) = 𝜈
		String receiverName = receiverVar.getName();
		SymbolicValue receiverValue = symbEnv.getOrThrow(receiverName);
		// Σ(𝜈) ≠ ⊥
		UniquenessAnnotation receiverPerm = permEnv.getOrThrow(receiverValue, "receiver " + receiverName);

		if (receiverPerm.isBottom()) {
			throw new IllegalStateException("Receiver is inaccessible in evaluation: " + receiverName);
		}

		// Δ(𝜈.𝑓) = 𝜈′
		String fieldName = fieldAccess.getField();
		SymbolicValue fieldValue = symbEnv.get(receiverValue, fieldName);

		if (fieldValue != null) {
			return evalField(receiverName, fieldName, fieldValue);
		}

		// field(Γ(𝑥), 𝑓) = 𝛼 𝐶
		CtTypeReference<?> receiverType = typeEnv.get(receiverName);
		if (receiverType == null) {
			throw new IllegalStateException("Missing type for receiver " + receiverName + " when evaluating " + receiverName + "." + fieldName);
		}
		UniquenessAnnotation declaredFieldPerm = maps.getFieldAnnotation(fieldName, receiverType);
		if (declaredFieldPerm == null) {
			throw new IllegalStateException("Unknown field " + fieldName + " on type " + receiverType);
		}

		if (isExclusiveReceiver(receiverPerm)) {
			return evalUniqueOrBorrowedField(receiverName, receiverValue, fieldName, declaredFieldPerm);
		}
		if (receiverPerm.isGreaterEqualThan(Uniqueness.SHARED) && declaredFieldPerm.isShared()) {
			return evalSharedField(receiverName, receiverValue, fieldName);
		}
		throw new IllegalStateException("Receiver " + receiverName + " with permission " + receiverPerm + " cannot access non-shared field " + fieldName);
	}

	/**
	 * EvalField
	 * Δ(𝑥) = 𝜈 Δ(𝜈.𝑓) = 𝜈′ Σ(𝜈) ≠ ⊥ Σ(𝜈′) ≠ ⊥
	 * ------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ Γ; Δ; Σ; 𝜑
	 */
	private SymbolicValue evalField(String receiverName, String fieldName, SymbolicValue fieldValue) {
		// Σ(𝜈′) ≠ ⊥
		UniquenessAnnotation fieldPerm = permEnv.getOrThrow(fieldValue, "field " + receiverName + "." + fieldName);
		if (fieldPerm.isBottom()) {
			throw new IllegalStateException("Field is inaccessible in evaluation: " + receiverName + "." + fieldName);
		}
		return fieldValue;
	}

	/**
	 * EvalUniqueOrBorrowedField
	 * Δ(𝑥) = 𝜈 Σ(𝜈) ∈ {unique, borrowed, free} 𝜈.𝑓 ∉ Δ field(Γ(𝑥), 𝑓) = 𝛼 𝐶 fresh 𝜈′
	 * ----------------------------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ 𝜈.𝑓: 𝜈′, Δ; 𝜈′: 𝛼, Σ; 𝜑
	 */
	private SymbolicValue evalUniqueOrBorrowedField(
			String receiverName,
			SymbolicValue receiverValue,
			String fieldName,
			UniquenessAnnotation declaredFieldPerm) {
		SymbolicValue fieldValue = symbEnv.addField(receiverValue, fieldName);
		permEnv.add(fieldValue, declaredFieldPerm);
		return evalField(receiverName, fieldName, fieldValue);
	}

	/**
	 * EvalSharedField
	 * Δ(𝑥) = 𝜈 shared ≤ Σ(𝜈) 𝜈.𝑓 ∉ Δ field(Γ(𝑥), 𝑓) = shared 𝐶 fresh 𝜈′
	 * ------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ 𝜈.𝑓 : 𝜈′, Δ; 𝜈′: shared, Σ; 𝜑
	 */
	private SymbolicValue evalSharedField(
			String receiverName,
			SymbolicValue receiverValue,
			String fieldName) {
		SymbolicValue fieldValue = symbEnv.addField(receiverValue, fieldName);
		permEnv.add(fieldValue, new UniquenessAnnotation(Uniqueness.SHARED));
		return evalField(receiverName, fieldName, fieldValue);
	}

	/**
	 * EvalConst
	 * fresh 𝜈
	 * -------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝑐 ⇓ 𝜈 ⊣ Δ; 𝜈: imm, Σ; 𝜑 ∧ (𝜈 == 𝑐)
	 */
	private SymbolicValue evalConstValue(Expression constant) {
		// fresh 𝜈
		SymbolicValue value = addImmutableFresh();
		refinementPath.addExpression(new BinaryExpression(new Var(value.toString()), BinaryOperator.EQ, constant));
		return value;
	}

	/**
	 *  EvalUnary
	 *	Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈1 ⊣ Δ′; Σ′ fresh 𝜈
	 *	if ⊕ ∈ {-, !}
	 *	-------------------------------------------
	 *	Γ; Δ; Σ ⊢ ⊕ 𝑒2 ⇓ 𝜈 ⊣ Δ′; 𝜈: imm, Σ′; 𝜑 ∧ (𝜈 == ⊕𝜈1)
	 */
	private SymbolicValue evalUnaryValue(UnaryExpression unaryExpression) {
		// Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈1 ⊣ Δ′; Σ′
		SymbolicValue operand = evalExpression(unaryExpression.getExpression());
		// fresh 𝜈
		SymbolicValue value = addImmutableFresh();
		refinementPath.addExpression(new BinaryExpression(new Var(value.toString()), BinaryOperator.EQ, new UnaryExpression(unaryExpression.getOperator(), new Var(operand.toString()))));
		return value;
	}

	/**
	 * 	EvalBinary
	 *  Γ; Δ; Σ; 𝜑 ⊢ 𝑒1 ⇓ 𝜈1 ⊣ Δ1; Σ1; 𝜑1
	 *	Γ; Δ1; Σ1; 𝜑1 ⊢ 𝑒2 ⇓ 𝜈2 ⊣ Δ2; Σ2; 𝜑2 fresh 𝜈
	 *	if ⊕ ∈ {+, -, *, /, == , < , || , &&}
	 *  ---------------------------------------------------------------------------
	 *	Γ; Δ; Σ; 𝜑 ⊢ 𝑒1 ⊕ 𝑒2 ⇓ 𝜈 ⊣ Δ2 ; 𝜈: imm, Σ2 ; 𝜑2 ∧ (𝜈 == 𝜈1 ⊕ 𝜈2)
	 */
	private SymbolicValue evalBinaryValue(BinaryExpression binaryExpression) {
		// Γ; Δ; Σ; 𝜑 ⊢ 𝑒1 ⇓ 𝜈1 ⊣ Δ1; Σ1; 𝜑1
		SymbolicValue left = evalExpression(binaryExpression.getLeft());
		// Γ; Δ1; Σ1; 𝜑1 ⊢ 𝑒2 ⇓ 𝜈 2 ⊣ Δ2; Σ2; 𝜑2
		SymbolicValue right = evalExpression(binaryExpression.getRight());
		// fresh 𝜈
		SymbolicValue value = addImmutableFresh();
		Expression symbolicOperation = new BinaryExpression(
			new Var(left.toString()),
			binaryExpression.getOperator(),
			new Var(right.toString()));
		refinementPath.addExpression(new BinaryExpression(new Var(value.toString()), BinaryOperator.EQ, symbolicOperation));
		return value;
	}

	private SymbolicValue addImmutableFresh() {
		SymbolicValue value = symbEnv.getFresh();
		permEnv.add(value, new UniquenessAnnotation(Uniqueness.IMMUTABLE));
		return value;
	}

	private boolean isExclusiveReceiver(UniquenessAnnotation perm) {
		return perm.isFree()
			|| perm.isUnique()
			|| perm.annotationEquals(Uniqueness.BORROWED);
	}
}
