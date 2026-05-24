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
		ContractContext ctx = beginConstructorContract(constructor);
		if (ctx != null) {
			contractStack.push(ctx);
		}
		enterScopes();

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

    // TODO: Check
	@Override
	public <T> void visitCtMethod(CtMethod<T> method) {
		logInfo("Visiting method <" + method.getSimpleName() + ">", method);
		ContractContext ctx = beginMethodContract(method);
		if (ctx != null) {
			contractStack.push(ctx);
		}
		enterScopes();

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
		
		SymbolicValue sv = symbEnv.addVariable(parameter.getSimpleName());
		UniquenessAnnotation ua = new UniquenessAnnotation(parameter);
		permEnv.add(sv, ua);
		logInfo(parameter.getSimpleName() + ": "+ sv);
		logInfo(sv + ": "+ ua);

		loggingSpaces--;
	}

	/**
	 * Visit Local Variable that can have only the variable declaration, or also an assignment
	 * Rules: CheckVarDecl + CheckVarAssign + CheckNew
	 * CheckVarDecl
	 *                     fresh 𝜈
	 * -----------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝐶 𝑥; ⊣ Γ[𝑥 ↦ → 𝐶]; 𝑥 : 𝜈, Δ; 𝑥 : ⊥, Σ
	 * 
	 * CheckVarAssign
	 * Γ(𝑥) = 𝐶 Γ ⊢ 𝑒 : 𝐶 Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈 ⊣ Δ′; Σ′; Δ′ [𝑥 ↦ → 𝜈]; Σ′ ⪰ Δ′′; Σ′′
	 * ------------------------------------------------------------------------
	 *             Γ; Δ; Σ ⊢ 𝑥 = 𝑒; ⊣ Γ; Δ′′; Σ′′;
	 * 
	 * 
	 * CheckNew
	 * ctor(𝐶) = 𝐶 (𝛼1 𝐶1 𝑥1, ..., 𝛼𝑛 𝐶𝑛 𝑥𝑛 )
	 * Γ ⊢ 𝑦 : 𝐶 Γ ⊢ 𝑒1, ..., 𝑒𝑛 : 𝐶1, ... , 𝐶𝑛
	 * Γ; Δ; Σ ⊢ 𝑒1, ... , 𝑒𝑛 ⇓ 𝜈1, ... , 𝜈𝑛 ⊣ Γ′; Δ′; Σ′ Σ′ ⊢ 𝑒1, ... , 𝑒𝑛 : 𝛼1, ... , 𝛼𝑛 ⊣ Σ′′
	 * distinct(Δ′, {𝜈𝑖 : borrowed ≤ 𝛼𝑖 }) fresh 𝜈′
	 * Δ′ [𝑦 ↦ → 𝜈′]; Σ′′ [𝜈 ↦ → free] ⪰ Δ′′; Σ′′′
	 * ------------------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝑦 = new 𝐶 (𝑒1, ..., 𝑒𝑛 ); ⊣ Γ; Δ′′; Σ′′′
	 * 
	 */
    // TODO: Check
	@Override
	public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
		logInfo("Visiting local variable <" + localVariable.getSimpleName() + ">", localVariable);
		loggingSpaces++;
        // CheckVarDecl
		// 1) Add the variable to the typing context
		
		String name = localVariable.getSimpleName();
		SymbolicValue v = symbEnv.addVariable(name);
		permEnv.add(v, new UniquenessAnnotation(Uniqueness.BOTTOM));

		// 2) Visit
		super.visitCtLocalVariable(localVariable);

		// CheckVarAssign
		// 3) Handle assignment
		CtElement value = localVariable.getAssignment();
		if (value != null) {
			SymbolicValue valueSV = (SymbolicValue) value.getMetadata(EVAL_KEY);
			if (valueSV == null) {
				logError(
					String.format("Local variable %s = %s has assignment with null symbolic value", name, localVariable.getAssignment().toString()),
					localVariable);
			} else {
				Object metadata = value.getMetadata(EVAL_KEY);
				if (metadata != null) {
					SymbolicValue vv = (SymbolicValue) metadata;
					symbEnv.addVarSymbolicValue(localVariable.getSimpleName(), vv);
					localVariable.putMetadata(EVAL_KEY, vv);
				} else {
					symbEnv.addVarSymbolicValue(localVariable.getSimpleName(), valueSV);
				}
				ClassLevelMaps.simplify(symbEnv, permEnv);
			}
		}

		logInfo("\nSymbolic Env: " + symbEnv.toString());
		logInfo("\nPermissions Env: " + permEnv.toString());
		loggingSpaces--;
	}

	@Override
	public <T> void visitCtInvocation(CtInvocation<T> invocation) {
		logInfo("Visiting invocation <" + invocation.toStringDebug() + ">", invocation);
		super.visitCtInvocation(invocation);

		String methodName = invocation.getExecutable().getSimpleName();
		if (methodName.equals("<init>")) {
			return;
		}

		if (invocation.getTarget() == null) {
			logError("Invocation needs to have a target but found none -", invocation);
		}

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
			SymbolicValue argSV = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (argSV == null) {
				logError("Symbolic value for invocation argument not found", invocation);
			}
			CtParameter<?> parameter = method.getParameters().get(i);
			UniquenessAnnotation expectedUA = new UniquenessAnnotation(parameter);
			UniquenessAnnotation actualUA = permEnv.get(argSV);

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
		if (!symbEnv.distinct(distinctArgs)) {
			logError(String.format("Non-distinct parameters in method call of %s", klass.getSimpleName()), invocation);
		}

		UniquenessAnnotation returnUA = new UniquenessAnnotation(method);
		SymbolicValue returnSV = symbEnv.addVariable(invocation.toString());
		permEnv.add(returnSV, returnUA);
		invocation.putMetadata(EVAL_KEY, returnSV);
		logInfo(String.format("Invocation %s:%s, %s:%s", invocation.toString(), returnSV, returnSV, returnUA));
	}

	@Override
	public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
		logInfo("Visiting field read <" + fieldRead.toStringDebug() + ">", fieldRead);
		loggingSpaces++;

		super.visitCtFieldRead(fieldRead);
		CtExpression<?> target = fieldRead.getTarget();
		CtFieldReference<?> field = fieldRead.getVariable();

		if (target instanceof CtVariableReadImpl || target instanceof CtThisAccessImpl) {
			SymbolicValue value;
			CtTypeReference<?> type = target.getType();
			value = (target instanceof CtVariableReadImpl)
				? symbEnv.get(((CtVariableReadImpl<?>) target).getVariable().getSimpleName())
				: symbEnv.get(THIS);

			UniquenessAnnotation perm = permEnv.get(value);
			SymbolicValue fieldValue = symbEnv.get(value, field.getSimpleName());
			if (perm.isGreaterEqualThan(Uniqueness.UNIQUE) && fieldValue == null) {
				UniquenessAnnotation fieldUA = maps.getFieldAnnotation(field.getSimpleName(), type);
				if (fieldUA == null) {
					logError(String.format("field annotation not found for %s", field.getSimpleName()), fieldRead);
				}
				fieldValue = symbEnv.addField(value, field.getSimpleName());
				permEnv.add(fieldValue, fieldUA);
				fieldRead.putMetadata(EVAL_KEY, fieldValue);
				logInfo(String.format("%s.%s: %s", value, field.getSimpleName(), fieldValue));
			} else if (perm.isGreaterEqualThan(Uniqueness.SHARED) && fieldValue == null) {
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
    // TODO: Check, might be better to have visitCtAssigment
    // handle.
	@Override
	public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
		logInfo("Visiting field write <" + fieldWrite.toStringDebug() + ">", fieldWrite);
		CtExpression<?> target = fieldWrite.getTarget();
		if (target instanceof CtVariableReadImpl) {
			SymbolicValue value = symbEnv.get(((CtVariableReadImpl<?>) target).getVariable().getSimpleName());
			target.putMetadata(EVAL_KEY, value);
			logInfo(((CtVariableReadImpl<?>) target).getVariable().getSimpleName() + ": " + value);
		} else if (target instanceof CtThisAccessImpl) {
			SymbolicValue value = symbEnv.get(THIS);
			target.putMetadata(EVAL_KEY, value);
			logInfo("this: " + value);
		} else {
			logError("Field write target not found", fieldWrite);
		}
	}

    // TODO: Check
	@Override
	public <T, A extends T> void visitCtAssignment(CtAssignment<T, A> assignment) {
		logInfo("Visiting assignment <" + assignment.toStringDebug() + ">", assignment);
		loggingSpaces++;
		super.visitCtAssignment(assignment);

		CtExpression<?> assignee = assignment.getAssigned();
		CtExpression<?> value = assignment.getAssignment();

		if (assignee instanceof CtVariableWriteImpl<?> varWrite) {
			if (value instanceof CtInvocation<?> invocation) {
				handleInvocationAssignment(varWrite, invocation, assignment);
			} else {
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
			UniquenessAnnotation fieldPerm = maps.getFieldAnnotation(field.getSimpleName(), targetType);

			SymbolicValue valueSV = (SymbolicValue) value.getMetadata(EVAL_KEY);
			SymbolicValue targetSV = (SymbolicValue) target.getMetadata(EVAL_KEY);
			UniquenessAnnotation valuePerm = permEnv.get(valueSV);

			if (!permEnv.usePermissionAs(valueSV, valuePerm, fieldPerm)) {
				logError(String.format("Expected %s but got %s", fieldPerm, valuePerm), assignment);
			}

			symbEnv.addFieldSymbolicValue(targetSV, field.getSimpleName(), valueSV);
		}

		ClassLevelMaps.simplify(symbEnv, permEnv);
		loggingSpaces--;
	}

    // TODO: Check
	@Override
	public <T> void visitCtConstructorCall(CtConstructorCall<T> constCall) {
		logInfo("Visiting constructor call <"+ constCall.toStringDebug()+">", constCall);
		super.visitCtConstructorCall(constCall);

		// Check if all arguments follow the restrictions
		if (constCall.getArguments().size() > 0)
			handleConstructorArgs(constCall);
		// Create a new symbolic value for the constructor
		SymbolicValue vv = symbEnv.getFresh();
		permEnv.add(vv, new UniquenessAnnotation(Uniqueness.FREE));
		constCall.putMetadata(EVAL_KEY, vv);
	}

	@Override
	public void visitCtIf(CtIf ifElement) {
		logInfo("Visiting if <"+ ifElement.toStringDebug()+">", ifElement);
		// super.visitCtIf(ifElement);

		// Evaluate the conditions
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

		enterScopes();
		super.visitCtBlock(ifElement.getThenStatement());
		SymbolicEnvironment thenSymbEnv = symbEnv.cloneLast();
		PermissionEnvironment thenPermEnv = permEnv.cloneLast();
		exitScopes();

		SymbolicEnvironment elseSymbEnv;
		PermissionEnvironment elsePermEnv;

		if (ifElement.getElseStatement() != null) {
			//Else statement
			enterScopes();
			super.visitCtBlock(ifElement.getElseStatement());
			elseSymbEnv = symbEnv.cloneLast();
			elsePermEnv = permEnv.cloneLast();
			exitScopes();
		} else {
			//No Else statement
			elseSymbEnv = symbEnv.cloneLast();
			elsePermEnv = permEnv.cloneLast();
		}

		joining(thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);
	}

	@Override
	public <R> void visitCtReturn(CtReturn<R> returnStatement) {
		logInfo("Visiting return <"+ returnStatement.toStringDebug()+">", returnStatement);
		super.visitCtReturn(returnStatement);

		CtExpression<?> returned = returnStatement.getReturnedExpression();
		if (returned == null) return;
		SymbolicValue vRet = (SymbolicValue) returned.getMetadata(EVAL_KEY);
		if (vRet == null) logError("Symbolic value for return not found:"+returned.toStringDebug(), returned);
		UniquenessAnnotation ua = permEnv.get(vRet);

		CtMethod<?> cmet = returnStatement.getParent(CtMethod.class);
		UniquenessAnnotation expectedUA = new UniquenessAnnotation(cmet);
	
		if(!permEnv.usePermissionAs(vRet, ua, expectedUA)){
			logError(String.format("Expected %s but got %s in return %s", 
				expectedUA, ua, returnStatement.toString()), returned);
		}

		ContractContext ctx = currentContract();
		if (ctx != null && ctx.post != null) {
			evaluatePreIfNeeded(ctx, returnStatement);
			// TODO: implement havoc and uncomment this	
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

		// Get a fresh symbolic value and add it to the environment with an immutable permission (EvalBinary)
		SymbolicValue sv = symbEnv.getFresh();
		UniquenessAnnotation ua = new UniquenessAnnotation(Uniqueness.IMMUTABLE);

		// Add the symbolic value to the environment with a shared default value
		permEnv.add(sv, ua);

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

		// Get a fresh symbolic value and add it to the environment with an immutable permission (EvalUnary)
		SymbolicValue sv = symbEnv.getFresh();
		UniquenessAnnotation ua = new UniquenessAnnotation(Uniqueness.IMMUTABLE);

		// Add the symbolic value to the environment with a shared default value
		permEnv.add(sv, ua);
		
		// Store the symbolic value in metadata
		operator.putMetadata(EVAL_KEY, sv);
		logInfo(operator.toStringDebug() + ": "+ sv);
		loggingSpaces--;
	}

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
		variableRead.putMetadata(EVAL_KEY, sv);
		logInfo(variableRead.toString() + ": " + sv);
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

		// Store the symbolic value in metadata
		literal.putMetadata(EVAL_KEY, sv);
		logInfo("Literal "+ literal.toString() + ": "+ sv);
	}

	public void joining(
		SymbolicEnvironment thenSymbEnv,
		PermissionEnvironment thenPermEnv,
		SymbolicEnvironment elseSymbEnv,
		PermissionEnvironment elsePermEnv) {
		logInfo("Joining if statement");
		if (thenSymbEnv.isEmpty() && elseSymbEnv.isEmpty()) {
			return;
		}

		ClassLevelMaps.joinDropVar(symbEnv, thenSymbEnv);
		ClassLevelMaps.joinDropVar(symbEnv, elseSymbEnv);
		ClassLevelMaps.joinDropField(symbEnv);

		ClassLevelMaps.joinUnify(symbEnv, permEnv, thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);
		ClassLevelMaps.joinElim(symbEnv, permEnv, thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);

		ClassLevelMaps.simplify(symbEnv, permEnv);
		logInfo("Joining finished! " + symbEnv + "\n " + permEnv);
	}

	private void handleInvocationAssignment(
		CtVariableWriteImpl<?> assignee,
		CtInvocation<?> invocation,
		CtAssignment<?, ?> assignment) {
		String methodName = invocation.getExecutable().getSimpleName();
		if (methodName.equals("<init>")) {
			return;
		}

		CtTypeReference<?> receiverType = invocation.getTarget().getType().getTypeErasure();
		CtClass<?> klass = maps.getClassFrom(receiverType);
		CtMethod<?> method = maps.getCtMethod(klass, methodName, invocation.getArguments().size());
		if (method == null) {
			return;
		}

		SymbolicValue returnSV = (SymbolicValue) invocation.getMetadata(EVAL_KEY);
		if (returnSV == null) {
			logError("Symbolic value for invocation return not found", assignment);
		}

		SymbolicValue freshTarget = symbEnv.addVariable(assignee.getVariable().getSimpleName());
		UniquenessAnnotation returnPerm = permEnv.get(returnSV);
		if (returnPerm == null) {
			returnPerm = new UniquenessAnnotation(Uniqueness.SHARED);
		}
		permEnv.add(freshTarget, returnPerm);

		RefinementContract contract = maps.getMethodContract(klass, methodName, invocation.getArguments().size());
		if (contract != null && contract.getFrom() != null) {
			try {
				Evaluator.PredicateEvalResult preResult = eval.evalPredicate(
					contract.getFrom(), buildInvocationTypeEnv(invocation), symbEnv, permEnv, this.refinementPath);
				this.refinementPath = preResult.getRefinementPath();
			} catch (IllegalStateException ex) {
				logError("Refinement precondition failed: " + ex.getMessage(), assignment);
			}
		}

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

		symbEnv.addVarSymbolicValue(assignee.getVariable().getSimpleName(), freshTarget);
		ClassLevelMaps.simplify(symbEnv, permEnv);
	}
    
	private void handleConstructorArgs(CtConstructorCall<?> constructorCall) {
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
			SymbolicValue argSV = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (argSV == null) {
				logError("Symbolic value for constructor argument not found", constructorCall);
			}

			CtParameter<?> parameter = constructor.getParameters().get(i);
			UniquenessAnnotation expectedUA = new UniquenessAnnotation(parameter);
			UniquenessAnnotation actualUA = permEnv.get(argSV);
			if (!actualUA.isGreaterEqualThan(Uniqueness.BORROWED)) {
				logError(String.format("Symbolic value %s:%s is not greater than BORROWED", argSV, actualUA), arg);
			}
			logInfo(String.format("Checking constructor argument %s:%s, %s <= %s", parameter.getSimpleName(), argSV, actualUA, expectedUA), constructorCall);
			if (!permEnv.usePermissionAs(argSV, actualUA, expectedUA)) {
				logError(String.format("Expected %s but got %s", expectedUA, actualUA), arg);
			}
			paramSymbValues.add(argSV);
		}

		if (!symbEnv.distinct(paramSymbValues)) {
			logError(String.format("Non-distinct parameters in constructor call of %s", klass.getSimpleName()), constructorCall);
		}
		logInfo("all distinct");
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
		try {
			Evaluator.PredicateEvalResult preResult = eval.evalPredicate(
				ctx.pre, ctx.typeEnv, symbEnv, permEnv, this.refinementPath);
			this.refinementPath = preResult.getRefinementPath();
			ctx.refinementPath = preResult.getRefinementPath();
		} catch (IllegalStateException ex) {
			logError("Refinement precondition failed for " + ctx.label + ": " + ex.getMessage(), location);
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
		private RefinementPath refinementPath;

		private ContractContext(Expression pre, Expression post, int expectedParams, String label) {
			this.pre = pre;
			this.post = post;
			this.expectedParams = expectedParams;
			this.label = label;
		}
	}
}