package typechecking;

import java.util.Map;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
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
import utils.Utils;

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
		SymbolicValue value = Utils.getOrThrow(symbEnv.get(var.getName()), "Unknown symbolic value for variable " + var.getName());
		// Σ(𝜈) ≠ ⊥
		UniquenessAnnotation perm = Utils.getOrThrow(permEnv.get(value), "Missing permission for variable " + var.getName());
		if (perm.isBottom()) {
			throw new IllegalStateException("Variable is inaccessible in evaluation: " + var.getName());
		}
		return value;
	}

	/**
	 * EvalField
	 * Δ(𝑥) = 𝜈 Δ(𝜈.𝑓) = 𝜈′ Σ(𝜈) ≠ ⊥ Σ(𝜈′) ≠ ⊥
	 * ------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ Γ; Δ; Σ; 𝜑
	 * 
	 * EvalUniqueOrBorrowedField
	 * Δ(𝑥) = 𝜈 Σ(𝜈) ∈ {unique, borrowed, free} 𝜈.𝑓 ∉ Δ field(Γ(𝑥), 𝑓) = 𝛼 𝐶 fresh 𝜈′
	 *	----------------------------------------------------------------------------
	 *	Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ 𝜈.𝑓: 𝜈′, Δ; 𝜈′: 𝛼, Σ; 𝜑
	 *
	 * EvalSharedField
	 * Δ(𝑥) = 𝜈 shared ≤ Σ(𝜈) 𝜈.𝑓 ∉ Δ field(Γ(𝑥), 𝑓) = shared 𝐶 fresh 𝜈′ 
	 * ------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ 𝜈.𝑓 : 𝜈′, Δ; 𝜈′: shared, Σ; 𝜑
	 * 
	 */
	private SymbolicValue evalFieldValue(FieldAccess fieldAccess) {
		Expression receiverExpr = fieldAccess.getReceiver();
		if (!(receiverExpr instanceof Var receiverVar)) {
			throw new IllegalStateException("Only variable receivers are supported in evaluation: " + receiverExpr);
		}

		// Δ(𝑥) = 𝜈
		String receiverName = receiverVar.getName();
		SymbolicValue receiverValue = Utils.getOrThrow(symbEnv.get(receiverName), "Unknown symbolic value for variable " + receiverName);
		// Σ(𝜈) ≠ ⊥
		UniquenessAnnotation receiverPerm = Utils.getOrThrow(permEnv.get(receiverValue), "Missing permission for receiver " + receiverName);

		if (receiverPerm.isBottom()) {
			throw new IllegalStateException("Receiver is inaccessible in evaluation: " + receiverName);
		}

		// Δ(𝜈.𝑓) = 𝜈′
		String fieldName = fieldAccess.getField();
		SymbolicValue fieldValue = symbEnv.get(receiverValue, fieldName);

		if (fieldValue == null) {
			// field(Γ(𝑥), 𝑓) = 𝛼 𝐶
			CtTypeReference<?> receiverType = Utils.getOrThrow(typeEnv != null ? typeEnv.get(receiverName) : null, "Missing type for receiver " + receiverName + " when evaluating " + receiverName + "." + fieldName);
			// field(Γ(x),f) = 𝛼 𝐶
			UniquenessAnnotation declaredFieldPerm = Utils.getOrThrow(maps.getFieldAnnotation(fieldName, receiverType), "Unknown field " + fieldName + " on type " + receiverType);

			// EvalUniqueOrBorrowedField
			if (isExclusiveReceiver(receiverPerm)) {
				// field(Γ(𝑥), 𝑓) = 𝛼 𝐶 fresh 𝜈′
				fieldValue = symbEnv.addField(receiverValue, fieldName);
				permEnv.add(fieldValue, declaredFieldPerm);
			// EvalSharedField
			} else if (receiverPerm.isGreaterEqualThan(Uniqueness.SHARED) && declaredFieldPerm.isShared()) {
				// field(Γ(𝑥), 𝑓) = shared 𝐶 fresh 𝜈′
				fieldValue = symbEnv.addField(receiverValue, fieldName);
				permEnv.add(fieldValue, new UniquenessAnnotation(Uniqueness.SHARED));
			} else {
				throw new IllegalStateException("Receiver " + receiverName + " with permission " + receiverPerm + " cannot access non-shared field " + fieldName);
			}
		}

		// Σ(𝜈′) ≠ ⊥
		UniquenessAnnotation fieldPerm = Utils.getOrThrow(permEnv.get(fieldValue), "Missing permission for field " + receiverName + "." + fieldName);
		if (fieldPerm.isBottom()) {
			throw new IllegalStateException("Field is inaccessible in evaluation: " + receiverName + "." + fieldName);
		}
		return fieldValue;
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
