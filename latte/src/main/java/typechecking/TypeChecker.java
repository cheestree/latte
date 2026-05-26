package typechecking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementContract;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.Expression;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtThisAccessImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import spoon.support.reflect.code.CtVariableWriteImpl;

public class TypeChecker extends LatteAbstractChecker {
	private final Evaluator eval;
	private final Deque<ContractContext> contractStack = new ArrayDeque<>();
    private RefinementPath refinementPath;

	public TypeChecker(
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		ClassLevelMaps maps,
        RefinementPath refinementPath
	) {
		super(symbEnv, permEnv, maps);
		this.eval = new Evaluator(maps);
		this.refinementPath = refinementPath;
		logInfo("[ Type Checker initialized ]");
	}

	@Override
	public <T> void visitCtClass(CtClass<T> ctClass) {
		logInfo("Visiting class: <" + ctClass.getSimpleName() + ">", ctClass);
		enterScopes();
		super.visitCtClass(ctClass);
		exitScopes();
	}

	@Override
	public <T> void visitCtConstructor(CtConstructor<T> constructor) {
		logInfo("Visiting constructor <" + constructor.getSimpleName() + ">", constructor);
		// T-wf: prepare ρ_pre/ρ_post before Γ; Δ; Σ are extended with formals.
		ContractContext ctx = beginConstructorContract(constructor);
		if (ctx != null) {
			contractStack.push(ctx);
		}
		enterScopes();

		// T-wf: Δ(this)=𝜈₀ and Σ(𝜈₀)=borrowed.
		SymbolicValue thv = symbEnv.addVariable(THIS);
		permEnv.add(thv, new UniquenessAnnotation(Uniqueness.BORROWED));
		if (ctx != null && ctx.expectedParams == 0) {
			evaluatePreIfNeeded(ctx, constructor);
		}

		super.visitCtConstructor(constructor);

		exitScopes();
		if (ctx != null) {
			contractStack.pop();
		}
	}

	/**
	 * T-Method
	 * Γ; Δ; Σ; 𝜑 ⊢ ρ_pre ⇓ ρ_pre′ ⊣ Γ₁; Δ₁; Σ₁; 𝜑₁
	 * Γ₁; Δ₁; Σ₁; 𝜑₁ ∧ ρ_pre′ ⊢ s̄ ⊣ Γ₂; Δ₂; Σ₂; 𝜑₂
	 * Γ₂; Δ₂; Σ₂; 𝜑₂ ⊢ xᵣ ⇓ 𝜈ᵣ ⊣ Γ₃; Δ₃; Σ₃; 𝜑₃
	 * Σ₃ ⊢ 𝜈ᵣ : α ⊣ Σ₄
	 * Γ₃ ⊢ xᵣ : C
	 * Γ₃; Δ₃; Σ₄; 𝜑₃ ⊢ₒ ρ_post ⇓ ρ_post′ ⊣ Γ₄; Δ₄; Σ₅; 𝜑₄
	 * Γ₄; Δ₄; Σ₅; 𝜑₄ ⊢SMT ρ_post′[𝜈ᵣ/result]
	 * --------------------------------------------------------------------------------
	 * Γ, Δ, Σ, 𝜑 ⊢ (ρ_pre >> ρ_post) α C m(α₀ C₀ this, αₙ Cₙ x̄) { s̄ return xᵣ; }
	 *
	 * T-Method-void: same shape, without xᵣ ⇓ 𝜈ᵣ, Σ ⊢ 𝜈ᵣ : α, and [𝜈ᵣ/result].
	 * Phase 3.1 implements ρ_pre evaluation and return permission checks.
	 */
	@Override
	public <T> void visitCtMethod(CtMethod<T> method) {
		logInfo("Visiting method <" + method.getSimpleName() + ">", method);
		// T-wf: prepare ρ_pre/ρ_post before Γ; Δ; Σ are extended with formals.
		ContractContext ctx = beginMethodContract(method);
		if (ctx != null) {
			contractStack.push(ctx);
		}
		enterScopes();

		// T-wf: Δ(this)=𝜈₀ and Σ(𝜈₀)=borrowed.
		SymbolicValue thv = symbEnv.addVariable(THIS);
		permEnv.add(thv, new UniquenessAnnotation(Uniqueness.BORROWED));
		if (ctx != null && ctx.expectedParams == 0) {
			evaluatePreIfNeeded(ctx, method);
		}

		super.visitCtMethod(method);
		exitScopes();
		if (ctx != null) {
			contractStack.pop();
		}
	}

	@Override
	public <T> void visitCtParameter(CtParameter<T> parameter) {
		logInfo("Visiting parameter <"+ parameter.getSimpleName()+">", parameter);
		loggingSpaces++;
		super.visitCtParameter(parameter);
		
		CtTypeReference<?> type = parameter.getType();
		// T-wf: Γ(x)=C, Δ(x)=𝜈, and Σ(𝜈)=α.
		SymbolicValue sv = symbEnv.addVariable(parameter.getSimpleName());
		UniquenessAnnotation ua = new UniquenessAnnotation(parameter);
		permEnv.add(sv, ua);
		logInfo(parameter.getSimpleName() + ": "+ sv);
		logInfo(sv + ": "+ ua);

		// T-Method/T-Method-void: evaluate ρ_pre once all formals are in Γ; Δ; Σ.
		ContractContext ctx = currentContract();
		if (ctx != null) {
			ctx.typeEnv.put(parameter.getSimpleName(), type);
			ctx.seenParams++;
			if (ctx.seenParams == ctx.expectedParams) {
				evaluatePreIfNeeded(ctx, parameter);
			}
		}

		loggingSpaces--;
	}

	/**
	 * CheckVarDecl
	 *                     fresh 𝜈
	 * -------------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ C x; ⊣ Γ[x ↦ C]; x:𝜈, Δ; 𝜈:⊥, Σ; 𝜑
	 * 
	 * CheckVarAssign
	 * Γ(x)=C    Γ ⊢ e:C
	 * Γ; Δ; Σ; 𝜑 ⊢ e ⇓ 𝜈 ⊣ Δ′; Σ′; 𝜑′
	 * Δ′[x ↦ 𝜈]; Σ′ ⪰ Δ′′; Σ′′
	 * -----------------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ x = e; ⊣ Γ; Δ′′; Σ′′; 𝜑′
	 */
	@Override
	public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
		logInfo("Visiting local variable <" + localVariable.getSimpleName() + ">", localVariable);
		loggingSpaces++;
		
		String name = localVariable.getSimpleName();
		// CheckVarDecl: fresh 𝜈; Δ[x ↦ 𝜈]; Σ[𝜈 ↦ ⊥].
		SymbolicValue v = symbEnv.addVariable(name);
		permEnv.add(v, new UniquenessAnnotation(Uniqueness.BOTTOM));

		// CheckVarAssign: Γ; Δ; Σ; 𝜑 ⊢ e ⇓ 𝜈.
		super.visitCtLocalVariable(localVariable);

		CtElement value = localVariable.getAssignment();
		if (value != null) {
			// CheckVarAssign: get RHS 𝜈 from the evaluated expression.
			SymbolicValue valueSV = (SymbolicValue) value.getMetadata(EVAL_KEY);
			if (valueSV == null) {
				logError(
					String.format("Local variable %s = %s has assignment with null symbolic value", name, localVariable.getAssignment().toString()),
					localVariable);
			} else {
				// CheckVarAssign: Δ′[x ↦ 𝜈].
				Object metadata = value.getMetadata(EVAL_KEY);
				if (metadata != null) {
					SymbolicValue vv = (SymbolicValue) metadata;
					symbEnv.addVarSymbolicValue(localVariable.getSimpleName(), vv);
					localVariable.putMetadata(EVAL_KEY, vv);
				} else {
					symbEnv.addVarSymbolicValue(localVariable.getSimpleName(), valueSV);
				}
				// CheckVarAssign: Δ′; Σ′ ⪰ Δ′′; Σ′′.
				ClassLevelMaps.simplify(symbEnv, permEnv);
			}
		}

		logInfo("\nSymbolic Env: " + symbEnv.toString());
		logInfo("\nPermissions Env: " + permEnv.toString());
		loggingSpaces--;
	}

	/**
	 * EvalArgs
	 * Γ; Δ; Σ; 𝜑 ⊢ x, e₁, ..., eₙ ⇓ 𝜈₀, ..., 𝜈ₙ ⊣ Δ₁; Σ₁; 𝜑₁
	 * Σ₁ ⊢ 𝜈₀, ..., 𝜈ₙ : α₀, ..., αₙ ⊣ Σ₂
	 * distinct(Δ₁, {𝜈ᵢ : borrowed ≤ αᵢ})
	 *
	 * CheckCall-V2: this method currently implements ① lookup, ② EvalArgs for ē,
	 * and part of ⑥ return allocation. TODO: prepare(y), CheckPre/SMT, havoc,
	 * full update, and AssumePost.
	 */
	@Override
	public <T> void visitCtInvocation(CtInvocation<T> invocation) {
		logInfo("Visiting invocation <" + invocation.toStringDebug() + ">", invocation);
		// EvalArgs: evaluate x, e₁, ..., eₙ before checking α₀, ..., αₙ.
		super.visitCtInvocation(invocation);

		String methodName = invocation.getExecutable().getSimpleName();
		if (methodName.equals("<init>")) {
			return;
		}

		if (invocation.getTarget() == null) {
			logError("Invocation needs to have a target but found none -", invocation);
		}

		// CheckCall-V2 ①: method(Γ(x), f) lookup.
		CtTypeReference<?> receiverType = invocation.getTarget().getType().getTypeErasure();
		CtClass<?> klass = maps.getClassFrom(receiverType);
		CtMethod<?> method = maps.getCtMethod(klass, methodName, invocation.getArguments().size());
		if (method == null) {
			logInfo("Cannot find method {" + methodName + "} for {} in the context");
			return;
		}

		List<SymbolicValue> argValues = new ArrayList<>();
		for (int i = 0; i < invocation.getArguments().size(); i++) {
			CtExpression<?> arg = invocation.getArguments().get(i);
			// EvalArgs: eᵢ ⇓ 𝜈ᵢ.
			SymbolicValue argSV = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (argSV == null) {
				logError("Symbolic value for invocation argument not found", invocation);
			}
			CtParameter<?> parameter = method.getParameters().get(i);
			UniquenessAnnotation expectedUA = new UniquenessAnnotation(parameter);
			UniquenessAnnotation actualUA = permEnv.get(argSV);

			// EvalArgs: Σ ⊢ 𝜈ᵢ : αᵢ ⊣ Σ′.
			logInfo(String.format("Checking invocation argument %s:%s, %s <= %s", parameter.getSimpleName(), argSV, actualUA, expectedUA));
			if (!permEnv.usePermissionAs(argSV, actualUA, expectedUA)) {
				logError(String.format("Expected %s but got %s", expectedUA, actualUA), arg);
			}
			argValues.add(argSV);
		}

		List<SymbolicValue> distinctArgs = new ArrayList<>();
		for (SymbolicValue argSV : argValues) {
			UniquenessAnnotation ua = permEnv.get(argSV);
			if (ua != null && ua.isGreaterEqualThan(Uniqueness.BORROWED)) {
				distinctArgs.add(argSV);
			}
		}
		// EvalArgs: distinct(Δ, {𝜈ᵢ : borrowed ≤ αᵢ}).
		if (!symbEnv.distinct(distinctArgs)) {
			logError(String.format("Non-distinct parameters in method call of %s", klass.getSimpleName()), invocation);
		}

		// CheckCall-V2 ⑥, partial: fresh 𝜈_ret; Σ[𝜈_ret ↦ α_ret].
		UniquenessAnnotation returnUA = new UniquenessAnnotation(method);
		SymbolicValue returnSV = symbEnv.addVariable(invocation.toString());
		permEnv.add(returnSV, returnUA);
		invocation.putMetadata(EVAL_KEY, returnSV);
		logInfo(String.format("Invocation %s:%s, %s:%s", invocation.toString(), returnSV, returnSV, returnUA));
	}

	/**
	 * EvalField
		Δ(𝑥) = 𝜈   Δ(𝜈.𝑓 ) = 𝜈′   Σ(𝜈) ≠ ⊥   Σ(𝜈′) ≠ ⊥
		----------------------------------------------
		Γ; Δ; Σ ⊢ 𝑥 .𝑓 ⇓ 𝜈′ ⊣ Γ; Δ; Σ
	 */
	@Override
	public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
		logInfo("Visiting field read <" + fieldRead.toStringDebug() + ">", fieldRead);
		loggingSpaces++;

		// EvalField: evaluate the target x before resolving Δ(𝜈.f).
		super.visitCtFieldRead(fieldRead);
		CtExpression<?> target = fieldRead.getTarget();
		CtFieldReference<?> field = fieldRead.getVariable();

		if (target instanceof CtVariableReadImpl || target instanceof CtThisAccessImpl) {
			SymbolicValue value;
			CtTypeReference<?> type = target.getType();
			// EvalField: Δ(x)=𝜈, with this resolved through Δ(this).
			value = (target instanceof CtVariableReadImpl)
				? symbEnv.get(((CtVariableReadImpl<?>) target).getVariable().getSimpleName())
				: symbEnv.get(THIS);

			UniquenessAnnotation perm = permEnv.get(value);
			SymbolicValue fieldValue = symbEnv.get(value, field.getSimpleName());
			if (perm.isGreaterEqualThan(Uniqueness.UNIQUE) && fieldValue == null) {
				// EvalField fresh/tracked case: if α > shared and Δ(𝜈.f) is absent,
				// create Δ(𝜈.f)=𝜈′ and Σ(𝜈′)=field(Γ(x), f).
				UniquenessAnnotation fieldUA = maps.getFieldAnnotation(field.getSimpleName(), type);
				if (fieldUA == null) {
					logError(String.format("field annotation not found for %s", field.getSimpleName()), fieldRead);
				}
				fieldValue = symbEnv.addField(value, field.getSimpleName());
				permEnv.add(fieldValue, fieldUA);
				fieldRead.putMetadata(EVAL_KEY, fieldValue);
				logInfo(String.format("%s.%s: %s", value, field.getSimpleName(), fieldValue));
			} else if (perm.isGreaterEqualThan(Uniqueness.SHARED) && fieldValue == null) {
				// EvalField shared case: shared receivers may only expose shared fields.
				UniquenessAnnotation fieldUA = maps.getFieldAnnotation(field.getSimpleName(), type);
				if (!fieldUA.isShared()) {
					logError(String.format("Field %s is not shared but %s is", field.getSimpleName(), value), fieldRead);
				} else {
					fieldValue = symbEnv.addField(value, field.getSimpleName());
					permEnv.add(fieldValue, fieldUA);
					fieldRead.putMetadata(EVAL_KEY, fieldValue);
					logInfo(String.format("%s.%s: %s", value, field.getSimpleName(), fieldValue));
				}
			} else {
				// EvalField existing case: require Σ(𝜈) ≠ ⊥ and Σ(𝜈′) ≠ ⊥.
				if (perm.isBottom()) {
					logError(String.format("%s:%s is not accepted in field evaluation", value, perm), fieldRead);
				}
				if (fieldValue == null) {
					symbEnv.addField(value, field.getSimpleName());
					logError(String.format("Could not find symbolic value for %s.%s", value, field.getSimpleName()), fieldRead);
				}
				UniquenessAnnotation fieldPerm = permEnv.get(fieldValue);
				if (fieldPerm.isBottom()) {
					logError(String.format("%s:%s is not accepted in field evaluation", fieldValue, fieldPerm), fieldRead);
				}
				fieldRead.putMetadata(EVAL_KEY, fieldValue);
				logInfo(String.format("%s.%s: %s", value, field.getSimpleName(), fieldValue));
			}
		}

		loggingSpaces--;
	}

	/**
	 * Visit a field write as a field assignment
	 * 
	 * CheckFieldAssign
	 * field(Γ(𝑥), 𝑓 ) = 𝛼 𝐶 Γ ⊢ 𝑒 : 𝐶 Γ; Δ; Σ; 𝜑 ⊢ 𝑒 ⇓ 𝜈′ ⊣ Δ′; Σ′; 𝜑′
	 * Γ; Δ′; Σ′; 𝜑′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′; 𝜑′′ Σ′′ ⊢ 𝜈′ : 𝛼 ⊣ Σ′′′ Δ′′ [𝜈.𝑓 ↦ → 𝜈′]; Σ′′′ ⪰ Δ′′′; Σ′′′′
	 * --------------------------------------------------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝑥 .𝑓 = 𝑒; ⊣ Γ; Δ′′′; Σ′′′′; 𝜑′′
	 */
	@Override
	public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
		logInfo("Visiting field write <" + fieldWrite.toStringDebug() + ">", fieldWrite);
		CtExpression<?> target = fieldWrite.getTarget();
		if (target instanceof CtVariableReadImpl) {
			// CheckFieldAssign: Γ; Δ′; Σ′; 𝜑′ ⊢ x ⇓ 𝜈.
			SymbolicValue value = symbEnv.get(((CtVariableReadImpl<?>) target).getVariable().getSimpleName());
			target.putMetadata(EVAL_KEY, value);
			logInfo(((CtVariableReadImpl<?>) target).getVariable().getSimpleName() + ": " + value);
		} else if (target instanceof CtThisAccessImpl) {
			// CheckFieldAssign: this is treated as x and resolves through Δ(this).
			SymbolicValue value = symbEnv.get(THIS);
			target.putMetadata(EVAL_KEY, value);
			logInfo("this: " + value);
		} else {
			logError("Field write target not found", fieldWrite);
		}
	}

	/**
	 * CheckVarAssign and CheckFieldAssign are completed after Spoon has
	 * visited the RHS and assigned EVAL_KEY metadata.
	 */
	@Override
	public <T, A extends T> void visitCtAssignment(CtAssignment<T, A> assignment) {
		logInfo("Visiting assignment <" + assignment.toStringDebug() + ">", assignment);
		loggingSpaces++;
		// CheckVarAssign / CheckFieldAssign: evaluate e ⇓ 𝜈′ before updating Δ.
		super.visitCtAssignment(assignment);

		CtExpression<?> assignee = assignment.getAssigned();
		CtExpression<?> value = assignment.getAssignment();

		if (assignee instanceof CtVariableWriteImpl<?> varWrite) {
			if (value instanceof CtInvocation<?> invocation) {
				handleInvocationAssignment(varWrite, invocation, assignment);
			} else {
				// CheckVarAssign: use RHS 𝜈 and write Δ[x ↦ 𝜈].
				SymbolicValue valueSV = (SymbolicValue) value.getMetadata(EVAL_KEY);
				if (valueSV == null) {
					logError("Symbolic value for assignment not found", assignment);
				}
				symbEnv.addVarSymbolicValue(varWrite.getVariable().getSimpleName(), valueSV);
			}
		} else if (assignee instanceof CtFieldWrite<?> fieldWrite) {
			logInfo("Visiting field write <" + fieldWrite.toStringDebug() + ">");

			CtExpression<?> target = fieldWrite.getTarget();
			CtFieldReference<?> field = fieldWrite.getVariable();
			CtTypeReference<?> targetType = target.getType();
			// CheckFieldAssign: field(Γ(x), f)=α C.
			UniquenessAnnotation fieldPerm = maps.getFieldAnnotation(field.getSimpleName(), targetType);

			// CheckFieldAssign: e ⇓ 𝜈′ and x ⇓ 𝜈.
			SymbolicValue valueSV = (SymbolicValue) value.getMetadata(EVAL_KEY);
			SymbolicValue targetSV = (SymbolicValue) target.getMetadata(EVAL_KEY);
			UniquenessAnnotation valuePerm = permEnv.get(valueSV);

			// CheckFieldAssign: Σ′′ ⊢ 𝜈′ : α ⊣ Σ′′′.
			if (!permEnv.usePermissionAs(valueSV, valuePerm, fieldPerm)) {
				logError(String.format("Expected %s but got %s", fieldPerm, valuePerm), assignment);
			}

			// CheckFieldAssign: Δ′′[𝜈.f ↦ 𝜈′].
			symbEnv.addFieldSymbolicValue(targetSV, field.getSimpleName(), valueSV);
		}

		// CheckVarAssign / CheckFieldAssign: Δ; Σ ⪰ Δ′; Σ′.
		ClassLevelMaps.simplify(symbEnv, permEnv);
		loggingSpaces--;
	}

	/**
	 * CheckNew
	 * constructor(C) = (ρ_pre >> ρ_post) C(α₁ C₁ f₁, ..., αₙ Cₙ fₙ)
	 * Γ ⊢ y : C
	 * Γ; Δ; Σ; 𝜑 ⊢ args(x₁, ..., xₙ : α₁, ..., αₙ) ⊣ 𝜈₁, ..., 𝜈ₙ; Δ₁; Σ₁; 𝜑₁
	 * Δ₃, Σ₃ ⊢ havoc(𝜈₀, 𝜈₁, ..., 𝜈ₙ) ⊣ Δ₄; Σ₄; O
	 * fresh 𝜈_new
	 * Δ₂ = Δ₁[y ↦ 𝜈_new]
	 * Σ₂ = Σ₁[𝜈_new ↦ unique]
	 * Γ; Δ₃; Σ₂; 𝜑₂; O ⊢ post(ρ_post, y, x̄, f̄, 𝜈_new) ⊣ Δ₄; Σ₃; 𝜑₃
	 * --------------------------------------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ y = new C(x̄); ⊣ Γ; Δ₄; Σ₃; 𝜑₃
	 *
	 * Phase 3.1 implements args and fresh 𝜈_new allocation. Havoc/post are TODO.
	 */
	@Override
	public <T> void visitCtConstructorCall(CtConstructorCall<T> constCall) {
		logInfo("Visiting constructor call <"+ constCall.toStringDebug()+">", constCall);
		// CheckNew ②: evaluate constructor arguments x̄ ⇓ 𝜈̄.
		super.visitCtConstructorCall(constCall);

		// CheckNew ②: Σ ⊢ 𝜈ᵢ : αᵢ and distinct borrowed-or-stronger args.
		if (constCall.getArguments().size() > 0)
			handleConstructorArgs(constCall);
		// CheckNew ④: fresh 𝜈_new; Σ[𝜈_new ↦ free].
		SymbolicValue vv = symbEnv.getFresh();
		permEnv.add(vv, new UniquenessAnnotation(Uniqueness.FREE));
		constCall.putMetadata(EVAL_KEY, vv);
	}

	/**
	 * EvalArgs for CheckNew:
	 * Γ; Δ; Σ; 𝜑 ⊢ x₁, ..., xₙ ⇓ 𝜈₁, ..., 𝜈ₙ ⊣ Δ₁; Σ₁; 𝜑₁
	 * Σ₁ ⊢ 𝜈₁, ..., 𝜈ₙ : α₁, ..., αₙ ⊣ Σ₂
	 * distinct(Δ₁, {𝜈ᵢ : borrowed ≤ αᵢ})
	 */
	private void handleConstructorArgs(CtConstructorCall<?> constructorCall) {
		// CheckNew ①: constructor(C) lookup.
		CtClass<?> klass = maps.getClassFrom(constructorCall.getType());
		int paramSize = constructorCall.getArguments().size();
		CtConstructor<?> constructor = maps.geCtConstructor(klass, paramSize);
		List<SymbolicValue> paramSymbValues = new ArrayList<>();
		if (klass == null || constructor == null) {
			logInfo(String.format("Cannot find the constructor for {} in the context", constructorCall.getType()), constructorCall);
			return;
		}
		for (int i = 0; i < paramSize; i++) {
			CtExpression<?> arg = constructorCall.getArguments().get(i);
			// EvalArgs: xᵢ ⇓ 𝜈ᵢ.
			SymbolicValue argSV = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (argSV == null) {
				logError("Symbolic value for constructor argument not found", constructorCall);
			}

			CtParameter<?> parameter = constructor.getParameters().get(i);
			UniquenessAnnotation expectedUA = new UniquenessAnnotation(parameter);
			UniquenessAnnotation actualUA = permEnv.get(argSV);
			// EvalArgs: borrowed ≤ αᵢ is required for the distinct set.
			if (!actualUA.isGreaterEqualThan(Uniqueness.BORROWED)) {
				logError(String.format("Symbolic value %s:%s is not greater than BORROWED", argSV, actualUA), arg);
			}
			// EvalArgs: Σ ⊢ 𝜈ᵢ : αᵢ ⊣ Σ′.
			logInfo(String.format("Checking constructor argument %s:%s, %s <= %s", parameter.getSimpleName(), argSV, actualUA, expectedUA), constructorCall);
			if (!permEnv.usePermissionAs(argSV, actualUA, expectedUA)) {
				logError(String.format("Expected %s but got %s", expectedUA, actualUA), arg);
			}
			paramSymbValues.add(argSV);
		}

		// EvalArgs: distinct(Δ, {𝜈ᵢ : borrowed ≤ αᵢ}).
		if (!symbEnv.distinct(paramSymbValues)) {
			logError(String.format("Non-distinct parameters in constructor call of %s", klass.getSimpleName()), constructorCall);
		}
		logInfo("all distinct");
	}

	@Override
	public void visitCtIf(CtIf ifElement) {
		logInfo("Visiting if <"+ ifElement.toStringDebug()+">", ifElement);
		// super.visitCtIf(ifElement);

		// CheckIf: Γ; Δ; Σ; 𝜑 ⊢ e ⇓ 𝜈_c ⊣ Δ₀; Σ₀; 𝜑₀.
		CtExpression<Boolean> condition = ifElement.getCondition();
		if (condition instanceof CtBinaryOperator) {
			visitCtBinaryOperator((CtBinaryOperator<?>) condition);
		} else if (condition instanceof CtUnaryOperator) {
			visitCtUnaryOperator((CtUnaryOperator<?>) condition);
		} else if (condition instanceof CtLiteral) {
			visitCtLiteral((CtLiteral<?>) condition);
		} else if (condition instanceof CtVariableRead) {
			visitCtVariableRead((CtVariableRead<?>) condition);
		} else if (condition instanceof CtFieldRead) {
			visitCtFieldRead((CtFieldRead<?>) condition);
		} else if (condition instanceof CtInvocation) {
			visitCtInvocation((CtInvocation<?>) condition);
		} else {
			logError("Cannot evaluate the condition of the if statement: " + condition.toString(), condition);
		}

		// CheckIf: Γ; Δ₀; Σ₀; 𝜑₀ ∧ 𝜈_c ⊢ s₁.
		enterScopes();
		super.visitCtBlock(ifElement.getThenStatement());
		SymbolicEnvironment thenSymbEnv = symbEnv.cloneLast();
		PermissionEnvironment thenPermEnv = permEnv.cloneLast();
		exitScopes();

		SymbolicEnvironment elseSymbEnv;
		PermissionEnvironment elsePermEnv;

		if (ifElement.getElseStatement() != null) {
			// CheckIf: Γ; Δ₀; Σ₀; 𝜑₀ ∧ ¬𝜈_c ⊢ s₂.
			enterScopes();
			super.visitCtBlock(ifElement.getElseStatement());
			elseSymbEnv = symbEnv.cloneLast();
			elsePermEnv = permEnv.cloneLast();
			exitScopes();
		} else {
			// CheckIf without else: the else branch is the unchanged incoming state.
			elseSymbEnv = symbEnv.cloneLast();
			elsePermEnv = permEnv.cloneLast();
		}

		// CheckIf: Δ₁; Σ₁; 𝜑₁ ∧^{𝜑₀}_{𝜈_c} Δ₂; Σ₂; 𝜑₂ ⇒ Δ′; Σ′; 𝜑′.
		joining(thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);
	}

	@Override
	public <R> void visitCtReturn(CtReturn<R> returnStatement) {
		logInfo("Visiting return <"+ returnStatement.toStringDebug()+">", returnStatement);
		super.visitCtReturn(returnStatement);

		CtExpression<?> returned = returnStatement.getReturnedExpression();
		if (returned == null) return;
		// T-Method: xᵣ ⇓ 𝜈ᵣ.
		SymbolicValue vRet = (SymbolicValue) returned.getMetadata(EVAL_KEY);
		if (vRet == null) logError("Symbolic value for return not found:"+returned.toStringDebug(), returned);
		UniquenessAnnotation ua = permEnv.get(vRet);

		CtMethod<?> cmet = returnStatement.getParent(CtMethod.class);
		UniquenessAnnotation expectedUA = new UniquenessAnnotation(cmet);
	
		// T-Method: Σ ⊢ 𝜈ᵣ : α ⊣ Σ′.
		if(!permEnv.usePermissionAs(vRet, ua, expectedUA)){
			logError(String.format("Expected %s but got %s in return %s", 
				expectedUA, ua, returnStatement.toString()), returned);
		}

		ContractContext ctx = currentContract();
		if (ctx != null && ctx.post != null) {
			evaluatePreIfNeeded(ctx, returnStatement);
			// T-Method TODO: ρ_post ⇓ ρ_post′ and ⊢SMT ρ_post′[𝜈ᵣ/result].
			/* 
			Evaluator.PredicateEvalResult postResult = eval.evalPredicate(
				ctx.post, ctx.typeEnv, symbEnv, permEnv, this.refinementPath);
			Expression postPredicate = postResult.getPredicate();
			if (this.refinementPath != null && postPredicate != null
				&& !eval.entails(this.refinementPath, postPredicate)) {
				logError("Refinement postcondition not satisfied for " + ctx.label, returnStatement);
			}
			*/
		} 
	}

	/**
	 * Rule EvalBinary
	 */
	@Override
	public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
		logInfo("Visiting binary operator <"+ operator.toStringDebug()+">", operator);
		loggingSpaces++;
		super.visitCtBinaryOperator(operator);

		CtExpression<?> left = operator.getLeftHandOperand();
		CtExpression<?> right = operator.getRightHandOperand();
		SymbolicValue leftSV = (SymbolicValue) left.getMetadata(EVAL_KEY);
		SymbolicValue rightSV = (SymbolicValue) right.getMetadata(EVAL_KEY);
		if (leftSV == null) {
			logInfo("Symbolic value for binary operator left operand not found", left);
		}
		if (rightSV == null) {
			logInfo("Symbolic value for binary operator right operand not found", right);
		}

		// Get a fresh symbolic value and add it to the environment with an immutable permission (EvalBinary)
		SymbolicValue sv = symbEnv.getFresh();
		UniquenessAnnotation ua = new UniquenessAnnotation(Uniqueness.IMMUTABLE);

		// Add the symbolic value to the environment with a shared default value
		permEnv.add(sv, ua);

		// Milestone 3.4: add the formal definition to the refinement path.
		// refinementPath = refinementPath.addExpression(sv == leftSV <operator> rightSV);

		// Store the symbolic value in metadata
		operator.putMetadata(EVAL_KEY, sv);
		logInfo(operator.toStringDebug() + ": "+ sv);
		loggingSpaces--;
	}

	/**
	 * Rule EvalUnary
	 */
	@Override
	public <T> void visitCtUnaryOperator(CtUnaryOperator<T> operator) {
		logInfo("Visiting unary operator <"+ operator.toStringDebug()+">", operator);
		loggingSpaces++;
		super.visitCtUnaryOperator(operator);

		CtExpression<?> operand = operator.getOperand();
		SymbolicValue operandSV = (SymbolicValue) operand.getMetadata(EVAL_KEY);
		if (operandSV == null) {
			logInfo("Symbolic value for unary operator operand not found", operand);
		}

		// Get a fresh symbolic value and add it to the environment with an immutable permission (EvalUnary)
		SymbolicValue sv = symbEnv.getFresh();
		UniquenessAnnotation ua = new UniquenessAnnotation(Uniqueness.IMMUTABLE);

		// Add the symbolic value to the environment with a shared default value
		permEnv.add(sv, ua);

		// Milestone 3.4: add the formal definition to the refinement path.
		// refinementPath = refinementPath.addExpression(sv == <operator> operandSV);
		
		// Store the symbolic value in metadata
		operator.putMetadata(EVAL_KEY, sv);
		logInfo(operator.toStringDebug() + ": "+ sv);
		loggingSpaces--;
	}

	/**
	 * Rule EvalVar
	 */
	@Override
	public <T> void visitCtLocalVariableReference(CtLocalVariableReference<T> reference) {
		logInfo("Visiting local variable reference <" + reference.toString() + ">", reference);
		loggingSpaces++;
		super.visitCtLocalVariableReference(reference);

		SymbolicValue sv = symbEnv.get(reference.getSimpleName());
		if (sv == null) {
			logError(String.format("Symbolic value for local variable %s not found in the symbolic environment", reference.getSimpleName()), reference);
		} else {
			UniquenessAnnotation ua = permEnv.get(sv);
			if (ua.isBottom()) {
				logInfo(String.format("%s: %s", sv, ua));
			} else {
				reference.putMetadata(EVAL_KEY, sv);
				logInfo(String.format("%s: %s", reference.getSimpleName(), sv));
			}
		}
		loggingSpaces--;
	}

	@Override
	public <T> void visitCtVariableRead(CtVariableRead<T> variableRead) {
		loggingSpaces++;
		logInfo("Visiting variable read <" + variableRead.toString() + ">", variableRead);
		super.visitCtVariableRead(variableRead);

		SymbolicValue sv = symbEnv.get(variableRead.getVariable().getSimpleName());
		if (sv == null) {
			logInfo(String.format("Symbolic value for variable %s not found in the symbolic environment", variableRead.getVariable().getSimpleName()), variableRead);
		} else {
			UniquenessAnnotation ua = permEnv.get(sv);
			if (ua == null || ua.isBottom()) {
				logInfo(String.format("%s:%s is not accepted in variable evaluation", sv, ua), variableRead);
			} else {
				variableRead.putMetadata(EVAL_KEY, sv);
				logInfo(variableRead.toString() + ": " + sv);
			}
		}
		loggingSpaces--;
	}

	/**
	 * Rule EvalConst
	 * Visit a literal, add a symbolic value to the environment and a permission of shared
	 */
	@Override
	public <T> void visitCtLiteral(CtLiteral<T> literal) {
		logInfo("Visiting literal <"+ literal.toString()+">", literal);
		
		super.visitCtLiteral(literal);

		// Get a fresh symbolic value and add it to the environment with an immutable default value
		SymbolicValue sv = symbEnv.getFresh();
		UniquenessAnnotation ua = new UniquenessAnnotation(Uniqueness.IMMUTABLE);

		if (literal.getValue() == null)
			ua = new UniquenessAnnotation(Uniqueness.FREE);  // its a null literal
		

		// Add the symbolic value to the environment with an immutable default value
		permEnv.add(sv, ua);

		// Milestone 3.4: add the formal definition to the refinement path.
		// refinementPath = refinementPath.addExpression(sv == literal.getValue());

		// Store the symbolic value in metadata
		literal.putMetadata(EVAL_KEY, sv);
		logInfo("Literal "+ literal.toString() + ": "+ sv);
	}

	/**
	 * Join
	 * Kᵥ = dom_var(Δ₁) ∩ dom_var(Δ₂)
	 * K = Kᵥ ∪ { k.f | k ∈ K, Δ₁(k.f) and Δ₂(k.f) defined }
	 * fresh 𝜈ₖ for each k ∈ K
	 * Δ′ = { k ↦ 𝜈ₖ | k ∈ Kᵥ } ∪ { 𝜈ₖ.f ↦ 𝜈ₖ.f | k.f ∈ K }
	 * Σ′ = (Σ₁ ⊓ Σ₂) ∪ { 𝜈ₖ : Σ₁(Δ₁(k)) ⊓ Σ₂(Δ₂(k)) | k ∈ K }
	 * --------------------------------------------------------------------------------
	 * Δ₁; Σ₁; 𝜑₁ ∧^{𝜑₀}_{𝜈_c} Δ₂; Σ₂; 𝜑₂ ⇒ Δ′; Σ′; 𝜑′
	 *
	 * Current implementation joins Δ and Σ; 𝜑 join equalities are TODO.
	 */
	public void joining(
		SymbolicEnvironment thenSymbEnv,
		PermissionEnvironment thenPermEnv,
		SymbolicEnvironment elseSymbEnv,
		PermissionEnvironment elsePermEnv) {
		logInfo("Joining if statement");
		if (thenSymbEnv.isEmpty() && elseSymbEnv.isEmpty()) {
			return;
		}

		// Join ①/②: keep only variables and fields present in both branches.
		ClassLevelMaps.joinDropVar(symbEnv, thenSymbEnv);
		ClassLevelMaps.joinDropVar(symbEnv, elseSymbEnv);
		ClassLevelMaps.joinDropField(symbEnv);

		// Join ③/④/⑥: mint joined 𝜈ₖ values and meet branch permissions.
		ClassLevelMaps.joinUnify(symbEnv, permEnv, thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);
		ClassLevelMaps.joinElim(symbEnv, permEnv, thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);

		// Join/Simplify: collapse redundant joined values when possible.
		ClassLevelMaps.simplify(symbEnv, permEnv);
		logInfo("Joining finished! " + symbEnv + "\n " + permEnv);
	}

	private void handleInvocationAssignment(
		CtVariableWriteImpl<?> assignee,
		CtInvocation<?> invocation,
		CtAssignment<?, ?> assignment) {
		// CheckCall-V2 assignment form: y = x.m(ē).
		String methodName = invocation.getExecutable().getSimpleName();
		if (methodName.equals("<init>")) {
			return;
		}

		// CheckCall-V2 ①: method(Γ(x), f) lookup.
		CtTypeReference<?> receiverType = invocation.getTarget().getType().getTypeErasure();
		CtClass<?> klass = maps.getClassFrom(receiverType);
		CtMethod<?> method = maps.getCtMethod(klass, methodName, invocation.getArguments().size());
		if (method == null) {
			return;
		}

		// CheckCall-V2 ⑥: retrieve 𝜈_ret allocated by visitCtInvocation.
		SymbolicValue returnSV = (SymbolicValue) invocation.getMetadata(EVAL_KEY);
		if (returnSV == null) {
			logError("Symbolic value for invocation return not found", assignment);
		}

		// PrepareTarget ③ / UpdatePerms ⑥: fresh target 𝜈′ carrying α_ret.
		SymbolicValue freshTarget = symbEnv.addVariable(assignee.getVariable().getSimpleName());
		UniquenessAnnotation returnPerm = permEnv.get(returnSV);
		if (returnPerm == null) {
			returnPerm = new UniquenessAnnotation(Uniqueness.SHARED);
		}
		permEnv.add(freshTarget, returnPerm);

		RefinementContract contract = maps.getMethodContract(klass, methodName, invocation.getArguments().size());
		if (contract != null && contract.getFrom() != null) {
			// CheckPre ④, partial: substituting call-site names is TODO;
			// phase 3.1 validates and assumes ρ_pre under current Γ; Δ; Σ.
			evaluateAndAssumePre(contract.getFrom(), buildInvocationTypeEnv(invocation), assignment, "");
		}

		// UpdatePerms ⑥: unique actuals become ⊥ after ownership transfer.
		for (CtExpression<?> arg : invocation.getArguments()) {
			SymbolicValue argSV = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (argSV == null) {
				continue;
			}
			UniquenessAnnotation perm = permEnv.get(argSV);
			if (perm != null && perm.isUnique()) {
				permEnv.add(argSV, new UniquenessAnnotation(Uniqueness.BOTTOM));
			}
		}

		// UpdatePerms ⑥: Δ[y ↦ 𝜈_ret] followed by simplification.
		symbEnv.addVarSymbolicValue(assignee.getVariable().getSimpleName(), freshTarget);
		ClassLevelMaps.simplify(symbEnv, permEnv);
	}

	private ContractContext beginMethodContract(CtMethod<?> method) {
		CtClass<?> klass = method.getParent(CtClass.class);
		if (klass == null) {
			return null;
		}
		RefinementContract contract = maps.getMethodContract(klass, method.getSimpleName(), method.getParameters().size());
		if (contract == null || (contract.getFrom() == null && contract.getTo() == null)) {
			return null;
		}
		ContractContext ctx = new ContractContext(contract.getFrom(), contract.getTo(), method.getParameters().size(), "method " + method.getSimpleName());
		CtType<?> declaringType = method.getDeclaringType();
		CtTypeReference<?> declaringTypeRef = declaringType == null ? null : declaringType.getReference();
		if (declaringTypeRef != null) {
			ctx.typeEnv.put(THIS, declaringTypeRef);
		}
		return ctx;
	}

	private ContractContext beginConstructorContract(CtConstructor<?> constructor) {
		CtClass<?> klass = constructor.getParent(CtClass.class);
		if (klass == null) {
			return null;
		}
		RefinementContract contract = maps.getConstructorContract(klass, constructor.getParameters().size());
		if (contract == null || (contract.getFrom() == null && contract.getTo() == null)) {
			return null;
		}
		ContractContext ctx = new ContractContext(contract.getFrom(), contract.getTo(), constructor.getParameters().size(), "constructor " + constructor.getSimpleName());
		CtTypeReference<?> declaringType = klass.getReference();
		if (declaringType != null) {
			ctx.typeEnv.put(THIS, declaringType);
		}
		return ctx;
	}

	private ContractContext currentContract() {
		return contractStack.peek();
	}

	private void evaluatePreIfNeeded(ContractContext ctx, CtElement location) {
		if (ctx.preEvaluated) {
			return;
		}
		ctx.preEvaluated = true;
		if (ctx.pre == null) {
			return;
		}
		evaluateAndAssumePre(ctx.pre, ctx.typeEnv, location, " for " + ctx.label);
	}

	private void evaluateAndAssumePre(
		Expression pre,
		Map<String, CtTypeReference<?>> typeEnv,
		CtElement location,
		String label) {
		try {
			// T-Method/CheckPre: Γ; Δ; Σ; 𝜑 ⊢ ρ_pre ⇓ ρ_pre′.
			Evaluator.PredicateEvalResult preResult = eval.evalPredicate(
				pre, typeEnv, symbEnv, permEnv, this.refinementPath);
			// T-Method/CheckPre: continue under 𝜑 ∧ ρ_pre′.
			RefinementPath path = preResult.refinementPath();
			if (preResult.predicate() != null) {
				path = path.addExpression(preResult.predicate());
			}
			this.refinementPath = path;
		} catch (IllegalStateException ex) {
			logError("Refinement precondition failed" + label + ": " + ex.getMessage(), location);
		}
	}

	private Map<String, CtTypeReference<?>> buildInvocationTypeEnv(CtInvocation<?> invocation) {
		Map<String, CtTypeReference<?>> typeEnv = new HashMap<>();
		if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
			typeEnv.put(THIS, invocation.getTarget().getType());
		}
		CtClass<?> klass = maps.getClassFrom(invocation.getTarget().getType().getTypeErasure());
		CtMethod<?> method = klass == null ? null : maps.getCtMethod(klass, invocation.getExecutable().getSimpleName(), invocation.getArguments().size());
		if (method != null) {
			for (CtParameter<?> parameter : method.getParameters()) {
				if (parameter.getType() != null) {
					typeEnv.put(parameter.getSimpleName(), parameter.getType());
				}
			}
		}
		return typeEnv;
	}

	private static final class ContractContext {
		private final Expression pre;
		private final Expression post;
		private final int expectedParams;
		private final String label;
		private final Map<String, CtTypeReference<?>> typeEnv = new HashMap<>();
		private int seenParams = 0;
		private boolean preEvaluated = false;

		private ContractContext(Expression pre, Expression post, int expectedParams, String label) {
			this.pre = pre;
			this.post = post;
			this.expectedParams = expectedParams;
			this.label = label;
		}
	}
}
