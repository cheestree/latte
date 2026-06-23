package typechecking;

import java.util.ArrayList;
import java.util.List;

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
import rj_language.ast.UnaryOperator;
import rj_language.ast.Var;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCatchVariable;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtThisAccessImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import spoon.support.reflect.code.CtVariableWriteImpl;

/**
 * In the type checker we go through the code, add metadata regarding the types and their permissions
 * and check if the code is well-typed
 */
public class LatteTypeChecker  extends LatteAbstractChecker {
	private final RefinementPath refPath;
	private final Evaluator evaluator;

	public LatteTypeChecker( TypeEnvironment typeEnv, SymbolicEnvironment symbEnv, 
							PermissionEnvironment permEnv, ClassLevelMaps mtc, RefinementPath refPath) {
		super(typeEnv, symbEnv, permEnv, mtc);
		this.refPath = refPath;
		this.evaluator = new Evaluator(
			maps,
			typeEnv,
			symbEnv,
			permEnv,
			refPath
		);
		logInfo("[ Latte Type checker initialized ]");
	}

	@Override
	public <T> void visitCtClass(CtClass<T> ctClass) {
		logInfo("Visiting class: <" + ctClass.getSimpleName()+">", ctClass);
		enterScopes();
		super.visitCtClass(ctClass);
		exitScopes();
	}
			
	
	@Override
	public <T> void visitCtConstructor(CtConstructor<T> c) {
		logInfo("Visiting constructor <"+ c.getSimpleName()+">", c);
		enterScopes();

		// Assume 'this' is a parameter always borrowed
		SymbolicValue thv = symbEnv.addVariable(THIS);
		permEnv.add(thv, new UniquenessAnnotation(Uniqueness.BORROWED));

		super.visitCtConstructor(c);

		exitScopes();
	}
	
	@Override
	public <T> void visitCtMethod(CtMethod<T> m) {
		logInfo("Visiting method <"+ m.getSimpleName()+">", m);
		enterScopes();

		// Assume 'this' is a parameter always borrowed
		SymbolicValue thv = symbEnv.addVariable(THIS);
		permEnv.add(thv, new UniquenessAnnotation(Uniqueness.BORROWED));

		super.visitCtMethod(m);
		exitScopes();
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
	 * Γ(𝑥) = 𝐶 Γ ⊢ 𝑒 : 𝐶 Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈 ⊣ Δ′; Σ′ Δ′ [𝑥 ↦ → 𝜈]; Σ′ ⪰ Δ′′; Σ′′
	 * ------------------------------------------------------------------------
	 *             Γ; Δ; Σ ⊢ 𝑥 = 𝑒; ⊣ Γ; Δ′′; Σ′′
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
	@Override
	public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
		logInfo("Visiting local variable <"+ localVariable.getSimpleName() +">", localVariable);
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
		if (value != null){
			SymbolicValue vValue = (SymbolicValue) value.getMetadata(EVAL_KEY);
			if (vValue == null) 
				logError(String.format("Local variable %s = %s has assignment with null symbolic value", name, 
					localVariable.getAssignment().toString()), localVariable);
			else{
				// If we already evaluated the value, we can get its symbolic value and associate it with the local variable
				Object metadata = value.getMetadata(EVAL_KEY);
				if (metadata != null){
					SymbolicValue vv = (SymbolicValue) metadata;
					symbEnv.addVarSymbolicValue(localVariable.getSimpleName(), vv);
					localVariable.putMetadata(EVAL_KEY, vv);
				} else {
					symbEnv.addVarSymbolicValue(localVariable.getSimpleName(), vValue);
				}
				ClassLevelMaps.simplify(symbEnv, permEnv);
			}
		}

		logInfo("\nSymbolic Env: " + symbEnv.toString());
		logInfo("\nPermissions Env: " + permEnv.toString());

		loggingSpaces--;
	}
					

	/**
	 * CheckCall
	 *  method(Γ(𝑥), 𝑓 ) = 𝛼 𝐶 𝑚(𝛼0 𝐶0 this, 𝛼1 𝐶1 𝑥1, · · · , 𝛼𝑛 𝐶𝑛 𝑥𝑛 )
	 *	Γ ⊢ 𝑦 : 𝐶 Γ ⊢ 𝑒0, · · · , 𝑒𝑛 : 𝐶0, · · · , 𝐶𝑛
	 *	Γ; Δ; Σ ⊢ 𝑒0, · · · , 𝑒𝑛 ⇓ 𝜈0, · · · , 𝜈𝑛 ⊣ Γ′; Δ′; Σ′ 
	 *	Σ′ ⊢ 𝑒0, · · · , 𝑒𝑛 : 𝛼0, · · · , 𝛼𝑛 ⊣ Σ′′
	 *	distinct(Δ′, {𝜈𝑖 : borrowed ≤ 𝛼𝑖 }) fresh 𝜈′
	 *	Δ′ [𝑦 ↦ → 𝜈′]; Σ′′ [𝜈 ↦ → 𝛼] ⪰ Δ′′; Σ′′′
	 * 	------------------------------------------------
	 *	Γ; Δ; Σ ⊢ 𝑦 = 𝑥 .𝑚(𝑒); ⊣ Γ; Δ′′; Σ′′′
	 */
	@Override
	public <T> void visitCtInvocation(CtInvocation<T> invocation) {
		logInfo("Visiting invocation <"+ invocation.toStringDebug()+">", invocation);
		super.visitCtInvocation(invocation);

		String metName = invocation.getExecutable().getSimpleName();

		if(metName.equals("<init>"))
			return;

		int paramSize = invocation.getArguments().size();

		if (invocation.getTarget() == null){
			logError("Invocation needs to have a target but found none -", invocation);
		}
		CtTypeReference<?> e = invocation.getTarget().getType().getTypeErasure();
		
		// method(Γ(𝑥), 𝑓 ) = 𝛼 𝐶 𝑚(𝛼0 𝐶0 this, 𝛼1 𝐶1 𝑥1, · · · , 𝛼𝑛 𝐶𝑛 𝑥𝑛 )
		CtClass<?> klass = maps.getClassFrom(e);
		CtMethod<?> m = maps.getCtMethod(klass, metName, 
			invocation.getArguments().size());

		if (m == null){
			logInfo("Cannot find method {" + metName + "} for {} in the context");
			// Method isn't found in the class maps, so we assign it shared permission and a fresh symbolic value.
			// This makes it so that we don't make any assumption about its permissions, but it allows the type checker to continue checking the rest of the code. If it was bottom, it would stop the type checking process, either from being consumed or inaccessible, while higher permissions would wrongly make assumptions like refinement paths that aren't true.
			SymbolicValue unknown = symbEnv.getFresh();
			permEnv.add(unknown, new UniquenessAnnotation(Uniqueness.SHARED));
			invocation.putMetadata(EVAL_KEY, unknown);
			return;
		}
		List<SymbolicValue> paramSymbValues = new ArrayList<>();

		for (int i = 0; i < paramSize; i++){
			CtExpression<?> arg = invocation.getArguments().get(i);
			// Γ; Δ; Σ ⊢ 𝑒1, ... , 𝑒𝑛 ⇓ 𝜈1, ... , 𝜈𝑛 ⊣ Γ′; Δ′; Σ′ 
			SymbolicValue vv = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (vv == null) logError("Symbolic value for constructor argument not found", invocation);
			
			CtParameter<?> p = m.getParameters().get(i);
			UniquenessAnnotation expectedUA = new UniquenessAnnotation(p);
			UniquenessAnnotation vvPerm = permEnv.get(vv);
			
			logInfo(String.format("Checking constructor argument %s:%s, %s <= %s", p.getSimpleName(), vv, vvPerm, expectedUA));
			// Σ′ ⊢ 𝑒1, ... , 𝑒𝑛 : 𝛼1, ... , 𝛼𝑛 ⊣ Σ′′
			if (!permEnv.usePermissionAs(vv, vvPerm, expectedUA))
				logError(String.format("Expected %s but got %s", expectedUA, vvPerm), arg);

			paramSymbValues.add(vv);
		}
		
		// distinct(Δ′, {𝜈𝑖 : borrowed ≤ 𝛼𝑖 })
		// distinct(Δ, 𝑆) ⇐⇒ ∀𝜈, 𝜈′ ∈ 𝑆 : Δ ⊢ 𝜈 ⇝ 𝜈′ =⇒ 𝜈 = 𝜈′
		List<SymbolicValue> check_distinct = new ArrayList<>();
		for(SymbolicValue sv: paramSymbValues)
			if (permEnv.get(sv).isGreaterEqualThan(Uniqueness.BORROWED))
				check_distinct.add(sv);

		if (!symbEnv.distinct(check_distinct)){
			logError(String.format("Non-distinct parameters in constructor call of %s", klass.getSimpleName()), invocation);
		}

		UniquenessAnnotation returnUA = new UniquenessAnnotation(m);
		SymbolicValue returnSV = symbEnv.addVariable(invocation.toString());
		permEnv.add(returnSV, returnUA);
		logInfo(String.format("Invocation %s:%s, %s:%s", invocation.toString(), returnSV, returnSV, returnUA));
		invocation.putMetadata(EVAL_KEY, returnSV);
	}

	/**
	 * EvalField
		Δ(𝑥) = 𝜈   Δ(𝜈.𝑓 ) = 𝜈′   Σ(𝜈) ≠ ⊥   Σ(𝜈′) ≠ ⊥
		----------------------------------------------
		Γ; Δ; Σ ⊢ 𝑥 .𝑓 ⇓ 𝜈′ ⊣ Γ; Δ; Σ
	 */
	@Override
	public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
		logInfo("Visiting field read <"+ fieldRead.toStringDebug()+">", fieldRead);
		loggingSpaces++;

		super.visitCtFieldRead(fieldRead);
		CtExpression<?> target = fieldRead.getTarget();
		CtFieldReference<?> f = fieldRead.getVariable();

		if ( target instanceof CtVariableReadImpl || target instanceof CtThisAccessImpl){
			SymbolicValue v;
			CtTypeReference<?> type = target.getType();
			v = (target instanceof CtVariableReadImpl) ? 
				symbEnv.get(((CtVariableReadImpl<?>)target).getVariable().getSimpleName()) : 
				symbEnv.get(THIS);

			// Δ(𝑥) = 𝜈 
			UniquenessAnnotation permV = permEnv.get(v);
			SymbolicValue vv = symbEnv.get(v, f.getSimpleName());
			// EVAL UNIQUE FIELD
			// 𝜈.𝑓 ∉ Δ
			if ( permV.isGreaterEqualThan(Uniqueness.UNIQUE) && vv == null) {
				//field(Γ(𝑥), 𝑓 ) = 𝛼 𝐶
				UniquenessAnnotation fieldUA = maps.getFieldAnnotation(f.getSimpleName(), type);
				if (fieldUA == null) logError(String.format("field annotation not found for %s", f.getSimpleName()), fieldRead);
				//----------------
				//𝜈.𝑓 : 𝜈′, Δ; 𝜈′: 𝛼, Σ   fresh 𝜈
				vv = symbEnv.addField(v, f.getSimpleName());
				permEnv.add(vv, fieldUA);

				// 𝑥 .𝑓 ⇓ 𝜈′
				fieldRead.putMetadata(EVAL_KEY, vv);
				logInfo(String.format("%s.%s: %s", v, f.getSimpleName(), vv));
			// EVAL SHARED FIELD
			} else if ( permV.isGreaterEqualThan(Uniqueness.SHARED) && vv == null){
				// field(Γ(𝑥), 𝑓 ) = shared 𝐶
				UniquenessAnnotation fieldUA = maps.getFieldAnnotation(f.getSimpleName(), type);
				if (!fieldUA.isShared()){
					logError(String.format("Field %s is not shared but %s is", f.getSimpleName(), v), fieldRead);
				} else {
					// 𝜈.𝑓 : 𝜈′, Δ; 𝜈′: shared, Σ
					vv = symbEnv.addField(v, f.getSimpleName());
					permEnv.add(vv, fieldUA);
					fieldRead.putMetadata(EVAL_KEY, vv);
					logInfo(String.format("%s.%s: %s", v, f.getSimpleName(), vv));
				}
			} else {
				//EVAL FIELD
				// Σ(𝜈) ≠ ⊥ 
				if (permV.isBottom()){
					logError(
						String.format("%s:%s is not accepted in field evaluation", v, permV)
						, fieldRead);
				}
				
				// Δ(𝜈.𝑓 ) = 𝜈′, if not present, add it 
				if (vv == null){
					symbEnv.addField(vv, f.getSimpleName());
					logError(String.format("Could not find symbolic value for %s.%s", v, f.getSimpleName())
						, fieldRead);
				}

				// Σ(𝜈′) ≠ ⊥
				UniquenessAnnotation permVV = permEnv.get(vv);
				if (permVV.isBottom()){
					logError(
						String.format("%s:%s is not accepted in field evaluation", vv, permVV)
						, fieldRead);
				}
				fieldRead.putMetadata(EVAL_KEY, vv);
				logInfo(String.format("%s.%s: %s", v, f.getSimpleName(), vv));
			}
		} 
		loggingSpaces--;
	}

	/**
	 * Visit a field write as a field assignment
	 * 
	 * CheckFieldAssign
	 * field(Γ(𝑥), 𝑓 ) = 𝛼 𝐶 Γ ⊢ 𝑒 : 𝐶 Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈′ ⊣ Δ′; Σ′
	 * Γ; Δ′; Σ′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′ Σ′′ ⊢ 𝜈′ : 𝛼 ⊣ Σ′′′ Δ′′ [𝜈.𝑓 ↦ → 𝜈′]; Σ′′′ ⪰ Δ′′′; Σ′′′′
	 * --------------------------------------------------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝑥 .𝑓 = 𝑒; ⊣ Γ; Δ′′′; Σ′′′′
	 */
	@Override
	public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
		logInfo("Visiting field write <"+ fieldWrite.toStringDebug()+">", fieldWrite);
		super.visitCtFieldWrite(fieldWrite);
		CtExpression<?> ce = fieldWrite.getTarget();
		if (ce instanceof CtVariableReadImpl){
			CtVariableReadImpl<?> x = (CtVariableReadImpl<?>) ce;
			SymbolicValue v = symbEnv.get(x.getVariable().getSimpleName());
			ce.putMetadata(EVAL_KEY, v);
			logInfo(x.getVariable().getSimpleName() + ": "+ v);
		} else if (ce instanceof CtThisAccessImpl){
			SymbolicValue v = symbEnv.get(THIS);
			ce.putMetadata(EVAL_KEY, v);
			logInfo("this: "+ v);
		} else {
			logError("Field write target not found", fieldWrite);
		}
	}

	/**
	 * Visit CTAssignment that can have a call, a new object, or an expression assignment
	 * Rules: CheckVarAssign + CheckNew + CheckCall
	 * 
	 * CheckVarAssign
	 * Γ(𝑥) = 𝐶 Γ ⊢ 𝑒 : 𝐶 Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈 ⊣ Δ′; Σ′ Δ′ [𝑥 ↦ → 𝜈]; Σ′ ⪰ Δ′′; Σ′′
	 * ------------------------------------------------------------------------
	 *             Γ; Δ; Σ ⊢ 𝑥 = 𝑒; ⊣ Γ; Δ′′; Σ′′
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
	@Override
	public <T, A extends T> void visitCtAssignment(CtAssignment<T, A> assignment) {
		logInfo("Visiting assignment <"+ assignment.toStringDebug()+">", assignment);
		loggingSpaces++;
		super.visitCtAssignment(assignment);

		CtExpression<?> assignee = assignment.getAssigned();
		CtExpression<?> value = assignment.getAssignment();

		if ( assignee instanceof CtVariableWriteImpl ){
			CtVariableWriteImpl<?> var = (CtVariableWriteImpl<?>) assignee;
			SymbolicValue targetSV = (SymbolicValue) value.getMetadata(EVAL_KEY);
			Object metadata = value.getMetadata(EVAL_KEY);
			if (metadata != null){
				SymbolicValue valueSV = (SymbolicValue) metadata;

				UniquenessAnnotation valuePerm = permEnv.get(valueSV);
				UniquenessAnnotation targetPerm = permEnv.get(targetSV);
				if (!permEnv.usePermissionAs(valueSV, valuePerm, targetPerm))
					logError(String.format("Expected %s but got %s", targetPerm, valuePerm, value), value);

				SymbolicValue fresh = symbEnv.addVariable(var.getVariable().getSimpleName());
				permEnv.add(fresh, targetPerm);
			} else {
				logError("BUG: Missing metadata for the assignment", var);
			}
		// Variable Assignment - CheckVarAssign
		} else if (assignee instanceof CtVariableWriteImpl){
			SymbolicValue v = (SymbolicValue) value.getMetadata(EVAL_KEY);
			if (v == null)
				logError("Symbolic value for assignment not found", assignment);
			symbEnv.addVarSymbolicValue(assignee.toString(), v);

		// Field Assignment - CheckFieldAssign
		} else if (assignee instanceof CtFieldWrite){
			CtFieldWrite<?> fieldWrite = (CtFieldWrite<?>) assignee;
			logInfo("Visiting field write <"+ fieldWrite.toStringDebug()+">");
	
			CtExpression<?> x = fieldWrite.getTarget();
			CtFieldReference<?> f = fieldWrite.getVariable();
			CtTypeReference<?> ct = x.getType();
			// field(Γ(𝑥), 𝑓 ) = 𝛼 𝐶
			UniquenessAnnotation fieldPerm = maps.getFieldAnnotation(f.getSimpleName(), ct);
	
			// Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈′ ⊣ Δ′; Σ′
			SymbolicValue vv = (SymbolicValue) value.getMetadata(EVAL_KEY);
			// Γ; Δ′; Σ′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′
			SymbolicValue v = (SymbolicValue) x.getMetadata(EVAL_KEY); 

			// Σ′′ ⊢ 𝜈′ : 𝛼 ⊣ Σ′′′
			UniquenessAnnotation vvPerm = permEnv.get(vv);

			// Check if we can use the permission of vv as the permission of the field
			if (!permEnv.usePermissionAs(vv, vvPerm, fieldPerm))
				logError(String.format("Expected %s but got %s", 
					fieldPerm, vvPerm), assignment);

			// Δ′′ [𝜈.𝑓 → 𝜈′]; Σ′′′ ⪰ Δ′′′; Σ′′′′
			symbEnv.addFieldSymbolicValue(v, f.getSimpleName(), vv);
		} 
		ClassLevelMaps.simplify(symbEnv, permEnv);
		loggingSpaces--;
	}


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

	/**
	 * Handle the constructor with arguments
	 * 
	 * CheckNew
	 * ctor(𝐶) = 𝐶 (𝛼1 𝐶1 𝑥1, ..., 𝛼𝑛 𝐶𝑛 𝑥𝑛 )
	 * Γ ⊢ 𝑦 : 𝐶 Γ ⊢ 𝑒1, ..., 𝑒𝑛 : 𝐶1, ... , 𝐶𝑛
	 * Γ; Δ; Σ ⊢ 𝑒1, ... , 𝑒𝑛 ⇓ 𝜈1, ... , 𝜈𝑛 ⊣ Γ′; Δ′; Σ′ 
	 * Σ′ ⊢ 𝑒1, ... , 𝑒𝑛 : 𝛼1, ... , 𝛼𝑛 ⊣ Σ′′
	 * distinct(Δ′, {𝜈𝑖 : borrowed ≤ 𝛼𝑖 }) fresh 𝜈′
	 * Δ′ [𝑦 → 𝜈′]; Σ′′ [𝜈 ↦ → free] ⪰ Δ′′; Σ′′′
	 * ------------------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝑦 = new 𝐶 (𝑒1, ..., 𝑒𝑛 ); ⊣ Γ; Δ′′; Σ′′′

	 * @param constCall
	 */
	private void handleConstructorArgs (CtConstructorCall<?> constCall){
		CtClass<?> klass = maps.getClassFrom(constCall.getType());
		int paramSize = constCall.getArguments().size();
		CtConstructor<?> c = maps.geCtConstructor(klass, paramSize);
		List<SymbolicValue> paramSymbValues = new ArrayList<>();
		if (klass == null || c == null){
			logInfo(String.format("Cannot find the constructor for {} in the context", constCall.getType()), constCall);
			return;
		}
		for (int i = 0; i < paramSize; i++){
			CtExpression<?> arg = constCall.getArguments().get(i);
			// Γ; Δ; Σ ⊢ 𝑒1, ... , 𝑒𝑛 ⇓ 𝜈1, ... , 𝜈𝑛 ⊣ Γ′; Δ′; Σ′ 
			SymbolicValue vv = (SymbolicValue) arg.getMetadata(EVAL_KEY);
			if (vv == null) logError("Symbolic value for constructor argument not found", constCall);
			
			CtParameter<?> p = c.getParameters().get(i);
			UniquenessAnnotation expectedUA = new UniquenessAnnotation(p);
			UniquenessAnnotation vvPerm = permEnv.get(vv);
			// {𝜈𝑖 : borrowed ≤ 𝛼𝑖 }
			if (!vvPerm.isGreaterEqualThan(Uniqueness.BORROWED)){
				logError(String.format("Symbolic value %s:%s is not greater than BORROWED", vv, vvPerm), arg);
			}
			logInfo(String.format("Checking constructor argument %s:%s, %s <= %s", p.getSimpleName(), vv, vvPerm, expectedUA), constCall);
			// Σ′ ⊢ 𝑒1, ... , 𝑒𝑛 : 𝛼1, ... , 𝛼𝑛 ⊣ Σ′′
			if (!permEnv.usePermissionAs(vv, vvPerm, expectedUA))
				logError(String.format("Expected %s but got %s", 
					 expectedUA, vvPerm), arg);
			paramSymbValues.add(vv);
		}

		// distinct(Δ′, {𝜈𝑖 : borrowed ≤ 𝛼𝑖 })
		//distinct(Δ, 𝑆) ⇐⇒ ∀𝜈, 𝜈′ ∈ 𝑆 : Δ ⊢ 𝜈 ⇝ 𝜈′ =⇒ 𝜈 = 𝜈′
		if (!symbEnv.distinct(paramSymbValues)){
			logError(String.format("Non-distinct parameters in constructor call of %s", klass.getSimpleName()), constCall);
		}
		logInfo("all distinct");
	}


	@Override
	public void visitCtIf(CtIf ifElement) {
		logInfo("Visiting if <"+ ifElement.toStringDebug()+">", ifElement);
		// super.visitCtIf(ifElement);

		// Evaluate the conditions
		CtExpression<Boolean> condition = ifElement.getCondition();
		if (condition instanceof CtBinaryOperator){
			visitCtBinaryOperator((CtBinaryOperator<?>)condition);
		} else if (condition instanceof CtUnaryOperator){
			visitCtUnaryOperator((CtUnaryOperator<?>)condition);
		} else if (condition instanceof CtLiteral){
			visitCtLiteral((CtLiteral<?>)condition);
		} else if (condition instanceof CtVariableRead){
			visitCtVariableRead((CtVariableRead<?>)condition);
		} else if (condition instanceof CtFieldRead){
			visitCtFieldRead((CtFieldRead<?>)condition);
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
	}


	/**
	 * Rule EvalBinary
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑒1 ⇓ 𝜈1 ⊣ Δ1; Σ1; 𝜑1
	 * Γ; Δ1; Σ1; 𝜑1 ⊢ 𝑒2 ⇓ 𝜈2 ⊣ Δ2; Σ2; 𝜑2 
	 * fresh 𝜈
	 * if ⊕ ∈ {+, -, *, /, ==, !=, <, >, <=, >=, || , &&}
	 * ---------------------------------------------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑒1 ⊕ 𝑒2 ⇓ 𝜈 ⊣ Δ2 ; 𝜈: imm, Σ2 ; 𝜑2 ∧ (𝜈 == 𝜈1 ⊕ 𝜈2)
	 */
	@Override
	public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
		logInfo("Visiting binary operator <"+ operator.toStringDebug()+">", operator);
		loggingSpaces++;
		// 𝑒1 ⇓ 𝜈1
		// 𝑒2 ⇓ 𝜈2
		super.visitCtBinaryOperator(operator);

		SymbolicValue operand1 = (SymbolicValue) operator.getLeftHandOperand().getMetadata(EVAL_KEY);
		SymbolicValue operand2 = (SymbolicValue) operator.getRightHandOperand().getMetadata(EVAL_KEY);
		if (operand1 == null || operand2 == null) {
			logError("Symbolic value for binary operator operand not found", operator);
			return;
		}

		// if ⊕ ∈ {+, -, *, /, ==, !=, <, >, <=, >=, || , &&}
		BinaryOperator op = SpoonToRjTranslator.toRjBinaryOperator(operator.getKind());
		if (op == null) {
			logError("Unsupported binary operator in evaluation: " + operator.getKind(), operator);
			return;
		}

		SymbolicValue sv = evaluator.evalBinary(operand1, op, operand2);

		// Store the symbolic value in metadata
		operator.putMetadata(EVAL_KEY, sv);
		logInfo(operator.toStringDebug() + ": "+ sv);
		loggingSpaces--;
	}
	/**
	 * EvalVar
	 * 𝑀 ::= (𝜌 » 𝜌) 𝜏 𝑚(𝜏 this, 𝜏 𝑥) { 𝑠 return 𝑒 ; }
	 * EvalVar applies directly. Spoon models it as CtThisAccess rather than CtVariableRead, requiring a separate visitor.
	 */
	@Override
	public <T> void visitCtThisAccess(CtThisAccess<T> thisAccess) {
		super.visitCtThisAccess(thisAccess);

		// Δ(this) = 𝜈
		SymbolicValue sv = symbEnv.get(THIS);
		if (sv == null) {
			logError("Symbolic value for this not found", thisAccess);
			return;
		}
		// Σ(𝜈) ≠ ⊥
		UniquenessAnnotation ua = permEnv.get(sv);
		if (ua.isBottom()) {
			logError(String.format("%s:%s is not accepted in this access evaluation", sv, ua), thisAccess);
			return;
		}
		thisAccess.putMetadata(EVAL_KEY, sv);
	}

	/**
	 * Rule EvalUnary
	 * Γ; Δ; Σ ⊢ 𝑒 ⇓ 𝜈1 ⊣ Δ′; Σ′ 
	 * fresh 𝜈
	 * if ⊕ ∈ {-, !}
	 * -------------------------------------------
	 * Γ; Δ; Σ ⊢ ⊕ 𝑒 ⇓ 𝜈 ⊣ Δ′; 𝜈: imm, Σ′; 𝜑 ∧ (𝜈 == ⊕𝜈1)
	 */
	@Override
	public <T> void visitCtUnaryOperator(CtUnaryOperator<T> operator) {
		logInfo("Visiting unary operator <"+ operator.toStringDebug()+">", operator);
		loggingSpaces++;
		// 𝑒 ⇓ 𝜈1
		super.visitCtUnaryOperator(operator);

		UnaryOperatorKind kind = operator.getKind();
		// Increment and decrement operators do not have a specific permission, so they're treated differently
		if (kind == UnaryOperatorKind.POSTINC || kind == UnaryOperatorKind.POSTDEC || kind == UnaryOperatorKind.PREINC  || kind == UnaryOperatorKind.PREDEC) {
			SymbolicValue sv = symbEnv.getFresh();
			permEnv.add(sv, new UniquenessAnnotation(Uniqueness.SHARED));
			operator.putMetadata(EVAL_KEY, sv);
			return;
		}

		SymbolicValue operand = (SymbolicValue) operator.getOperand().getMetadata(EVAL_KEY);
		if (operand == null) {
			logError("Symbolic value for unary operand not found", operator);
			return;
		}

		// if ⊕ ∈ {-, !}
		UnaryOperator op = SpoonToRjTranslator.toRjUnaryOperator(operator.getKind());
		if (op == null) {
			logError("Unsupported unary operator in evaluation: " + operator.getKind(), operator);
			return;
		}

		SymbolicValue sv = evaluator.evalUnary(op, operand);

		// Store the symbolic value in metadata
		operator.putMetadata(EVAL_KEY, sv);
		logInfo(operator.toStringDebug() + ": "+ sv);
		loggingSpaces--;
	}

	/**
	 * Rule EvalVar
	 * 
	 * Both CtVariableRead and CtLocalVariableReference have the same rule, but CtLocalVariableReference is used for local variables, while CtVariableRead is used for variables in general (including fields and parameters). Coverage of both is necessary to ensure that all variable references are handled correctly in the symbolic evaluation.
	 * TODO: Check if we can merge the two methods into one, as they have the same logic. If not, we can keep them separate for clarity.
	 */
	@Override
	public <T> void visitCtLocalVariableReference(CtLocalVariableReference<T> reference) {
		logInfo("Visiting local variable reference <"+ reference.toString()+">", reference);
		loggingSpaces++;
		super.visitCtLocalVariableReference(reference);
		
		SymbolicValue sv = symbEnv.get(reference.getSimpleName());
		if (sv == null) {
			logError(String.format("Symbolic value for local variable %s not found in the symbolic environment",
				reference.getSimpleName()), reference);
		} else{
			UniquenessAnnotation ua = permEnv.get(sv);
			if (ua.isBottom()){
				logInfo(String.format("%s: %s", sv, ua));
			} else {
				reference.putMetadata(EVAL_KEY, sv);
				logInfo(String.format("%s: %s", reference.getSimpleName(), sv));
			}
		}
		loggingSpaces--;
	}

	/**
	 *  EvalVar
	 *  Δ(𝑥) = 𝜈 Σ(𝜈) ≠ ⊥
	 *  ----------------------------------
	 *  Γ; Δ; Σ; 𝜑 ⊢ 𝑥 ⇓ 𝜈 ⊣ Γ; Δ; Σ; 𝜑
	 */
	@Override
	public <T> void visitCtVariableRead(CtVariableRead<T> variableRead) {
		loggingSpaces++;
		logInfo("Visiting variable read <"+ variableRead.toString()+">", variableRead);
		super.visitCtVariableRead(variableRead);

		// Δ(𝑥) = 𝜈
		SymbolicValue sv = symbEnv.get(variableRead.getVariable().getSimpleName());
		if (sv == null) {
			logError(String.format("Symbolic value for variable %s not found in the symbolic environment", variableRead.getVariable().getSimpleName()), variableRead);
		}
		variableRead.putMetadata(EVAL_KEY, sv);

		// Σ(𝜈) ≠ ⊥
		UniquenessAnnotation ua = permEnv.get(sv);
		if (ua.isBottom()){
			logError(String.format("%s:%s is not accepted in variable read evaluation", sv, ua), variableRead);
		}
		logInfo(variableRead.toString() + ": "+ sv);
		loggingSpaces--;
	}

	/**
	 * Rule EvalCatch
	 * Visit a catch block, add the exception variable to the symbolic environment with a borrowed permission. This is necessary because previously the symbol of the exception was added as null, but given the new null check in the rules, we need to add it with a borrowed permission to avoid errors in the evaluation of local variable reads of the exception variable.
	 */
	@Override
	public void visitCtCatch(CtCatch catchBlock) {
    	// The exception variable is added to the symbolic environment with a borrowed permission, as we don't own the exception, we just catch it
		CtCatchVariable<?> param = catchBlock.getParameter();
		SymbolicValue sv = symbEnv.addVariable(param.getSimpleName());
		// Borrowed permission because we don't own it, we just catch it
		permEnv.add(sv, new UniquenessAnnotation(Uniqueness.BORROWED));
		super.visitCtCatch(catchBlock);
	}

	/**
	 * Rule EvalConst
	 * Visit a literal, add a symbolic value to the environment and a permission of shared
	 * fresh 𝜈
	 * -------------------------------------------
	 * Γ; Δ; Σ ⊢ 𝑐 ⇓ 𝜈 ⊣ Δ; 𝜈: imm, Σ; 𝜑 ∧ (𝜈 == 𝑐)
	 */
	@Override
	public <T> void visitCtLiteral(CtLiteral<T> literal) {
		logInfo("Visiting literal <"+ literal.toString()+">", literal);
		
		super.visitCtLiteral(literal);

		// Get a fresh symbolic value and add it to the environment with an immutable default value
		// fresh 𝜈
		SymbolicValue sv = symbEnv.getFresh();

		// Grammar doesn't allow null literals, but we want to treat them as free values, so we check for null and assign the appropriate permission
		if (literal.getValue() == null) {
			permEnv.add(sv, new UniquenessAnnotation(Uniqueness.FREE));
		} else {
			// 𝜈: imm
			permEnv.add(sv, new UniquenessAnnotation(Uniqueness.IMMUTABLE));
			Expression constant = SpoonToRjTranslator.toRjLiteral(literal);
			if (constant != null) {
				// 𝜑 ∧ (𝜈 == 𝑐)
				refPath.addExpression(new BinaryExpression(new Var(sv.toString()), BinaryOperator.EQ, constant));
			}
		}

		// Store the symbolic value in metadata
		literal.putMetadata(EVAL_KEY, sv);
		logInfo("Literal "+ literal.toString() + ": "+ sv);
	}


	/**
	 * Performs the joining operation after the if statement
	 * @param thenSymbEnv
	 * @param thenPermEnv
	 * @param elseSymbEnv
	 * @param elsePermEnv
	 */
	public void joining( SymbolicEnvironment thenSymbEnv,
		PermissionEnvironment thenPermEnv, SymbolicEnvironment elseSymbEnv,
		PermissionEnvironment elsePermEnv) {
		
		logInfo("Joining if statement");
		// JoinEmpty
		// ∅ ⊢ ∅; Σ1 ∧ ∅; Σ2 ⇛ ∅; ∅
		if (thenSymbEnv.isEmpty() && elseSymbEnv.isEmpty())
			return;
		
		// JoinDropVar
		// 𝑥 ∉ Δ Δ ⊢ Δ1; Σ1 ∧ Δ2; Σ2 ⇛ Δ′; Σ′
		// ----------------------------------
		// Δ ⊢ 𝑥: 𝜈, Δ1; Σ1 ∧ Δ2; Σ2 ⇛ Δ′; Σ′
		ClassLevelMaps.joinDropVar(symbEnv, thenSymbEnv);
		ClassLevelMaps.joinDropVar(symbEnv, elseSymbEnv);
		ClassLevelMaps.joinDropField(symbEnv);

		ClassLevelMaps.joinUnify(symbEnv, permEnv, thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);
		ClassLevelMaps.joinElim(symbEnv, permEnv, thenSymbEnv, thenPermEnv, elseSymbEnv, elsePermEnv);

		ClassLevelMaps.simplify(symbEnv, permEnv);

		logInfo("Joining finished! "+ symbEnv + "\n "+ permEnv);
	}

	private void evaluateAndAssumePre(Expression pre) {
		Expression prePredicate = evaluator.eval(pre);
		if (prePredicate != null) {
			refPath.addExpression(prePredicate);
		}
	}
}
