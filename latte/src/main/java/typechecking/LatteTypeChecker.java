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
import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.UnaryOperator;
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
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
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
		typeEnv.add(THIS, c.getDeclaringType().getReference());
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
		typeEnv.add(THIS, m.getDeclaringType().getReference());
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
		
		typeEnv.add(parameter.getSimpleName(), parameter.getType());
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
		typeEnv.add(name, localVariable.getType());
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
			SymbolicValue externalResult = symbEnv.getFresh();
			permEnv.add(externalResult, new UniquenessAnnotation(Uniqueness.SHARED));
			invocation.putMetadata(EVAL_KEY, externalResult);
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
		Δ(𝑥) = 𝜈   Δ(𝜈.𝑓) = 𝜈′   Σ(𝜈) ≠ ⊥   Σ(𝜈′) ≠ ⊥
		----------------------------------------------
		Γ; Δ; Σ ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ Γ; Δ; Σ

		EvalUniqueOrBorrowedField
		Δ(𝑥) = 𝜈
		Σ(𝜈) ∈ {unique, borrowed, free}
		𝜈.𝑓 ∉ Δ
		field(Γ(𝑥), 𝑓) = 𝛼 𝐶
		fresh 𝜈′
		--------------------------------------------------
		Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ 𝜈.𝑓 : 𝜈′, Δ; 𝜈′: 𝛼, Σ; 𝜑

		EvalSharedField
		Δ(𝑥) = 𝜈
		shared ≤ Σ(𝜈)
		𝜈.𝑓 ∉ Δ
		field(Γ(𝑥), 𝑓) = shared 𝐶
		fresh 𝜈′
		------------------------------------------------------
		Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 ⇓ 𝜈′ ⊣ 𝜈.𝑓 : 𝜈′ , Δ; 𝜈′: shared, Σ; 𝜑
	 */
	@Override
	public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
		logInfo("Visiting field read <"+ fieldRead.toStringDebug()+">", fieldRead);
		loggingSpaces++;

		super.visitCtFieldRead(fieldRead);
		CtExpression<?> target = fieldRead.getTarget();
		if (target instanceof CtTypeAccess<?>) {
			// To avoid evaluation problems with external Java APIs (System.out.println, etc), we treat static fields as a special case and assign them a fresh symbolic value with shared permission, as it's the least restrictive and allows us to use external APIs without assuming ownership of their values.
			SymbolicValue staticFieldValue = symbEnv.getFresh();
			permEnv.add(staticFieldValue, new UniquenessAnnotation(Uniqueness.SHARED));
			fieldRead.putMetadata(EVAL_KEY, staticFieldValue);
			loggingSpaces--;
			return;
		}

		String name = getValueOrLog(receiverName(target), fieldRead, "Receiver name for field access not found for target %s");
		String fieldName = fieldRead.getVariable().getSimpleName();
		SymbolicValue fieldValue;
		try {
			fieldValue = evaluator.evalField(name, fieldName);
		} catch (IllegalStateException exception) {
			logError(exception.getMessage(), fieldRead);
			return;
		}

		fieldRead.putMetadata(EVAL_KEY, fieldValue);
		logInfo(String.format("%s.%s: %s", name, fieldName, fieldValue));
		loggingSpaces--;
	}

	/**
	 * Visit a field write as a field assignment
	 * 
	 * CheckFieldAssign
	 *  field(Γ(𝑥), 𝑓) = 𝛼 𝐶
	 *	Γ ⊢ 𝑒 : 𝐶
	 *	Γ; Δ; Σ; 𝜑 ⊢ 𝑒 ⇓ 𝜈′ ⊣ Δ′; Σ′; 𝜑′
	 *	Γ; Δ′; Σ′; 𝜑′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′; 𝜑′′
	 *	Σ′′ ⊢ 𝜈′: 𝛼 ⊣ Σ′′′
	 *	Δ′′ [𝜈.𝑓 ↦→ 𝜈′]; Σ′′′ ⪰ Δ′′′; Σ′′′′
	 *  -------------------------------------------------
	 *	Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 = 𝑒; ⊣ Γ; Δ′′′; Σ′′′′; 𝜑′′

	 * only Γ; Δ′; Σ′; 𝜑′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′; 𝜑′′ is checked here
	 */
	@Override
	public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
		logInfo("Visiting field write <"+ fieldWrite.toStringDebug()+">", fieldWrite);
		super.visitCtFieldWrite(fieldWrite);

		CtExpression<?> target = fieldWrite.getTarget();
		String name;
		if (target instanceof CtVariableRead<?> variableRead) {
			name = variableRead.getVariable().getSimpleName();
		} else if (target instanceof CtThisAccess<?>) {
			name = THIS;
		} else {
			logError("Receiver for field write is not supported: " + target.toStringDebug(), fieldWrite);
			return;
		}
		// Γ; Δ′; Σ′; 𝜑′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′; 𝜑′′
		SymbolicValue receiverValue = getValueOrLog(symbEnv.get(name), fieldWrite, "Symbolic value for field receiver %s not found");

		target.putMetadata(EVAL_KEY, receiverValue);
		logInfo(String.format("%s: %s", name, receiverValue));
	}

	private String receiverName(CtExpression<?> target) {
		if (target instanceof CtVariableRead<?> variableRead) {
			return variableRead.getVariable().getSimpleName();
		}
		if (target instanceof CtThisAccess<?>) {
			return THIS;
		}
		return null;
	}

	/**
	 * Visit CTAssignment that can have a variable assignment or a field assignment
	 * Rules: CheckVarAssign + CheckFieldAssign
	 * 
	 * CheckVarAssign
	 * Γ(𝑥) = 𝐶 
	 * Γ ⊢ 𝑒 : 𝐶 
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑒 ⇓ 𝜈 ⊣ Δ′; Σ′; 𝜑′
	 * Δ′ [𝑥 ↦ → 𝜈]; Σ′ ⪰ Δ′′; Σ′′
	 * ------------------------------------
	 * Γ; Δ; Σ; 𝜑 ⊢ 𝑥 = 𝑒; ⊣ Γ; Δ′′; Σ′′; 𝜑′
	 * 
	 * 
	 * CheckFieldAssign
	 *  field(Γ(𝑥), 𝑓) = 𝛼 𝐶
	 *  Γ ⊢ 𝑒 : 𝐶
	 *  Γ; Δ; Σ; 𝜑 ⊢ 𝑒 ⇓ 𝜈′ ⊣ Δ′; Σ′; 𝜑′
	 *	Γ; Δ′; Σ′; 𝜑 ′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′; 𝜑 ′′
	 *	Σ′′ ⊢ 𝜈′ : 𝛼 ⊣ Σ′′′
	 *	Δ′′ [𝜈.𝑓 ↦→ 𝜈′]; Σ′′′ ⪰ Δ′′′; Σ′′′′
	 *  -------------------------------------------------
	 *	Γ; Δ; Σ; 𝜑 ⊢ 𝑥.𝑓 = 𝑒 ; ⊣ Γ; Δ′′′; Σ′′′′; 𝜑′′
	 */
	@Override
	public <T, A extends T> void visitCtAssignment(CtAssignment<T, A> assignment) {
		logInfo("Visiting assignment <"+ assignment.toStringDebug()+">", assignment);
		loggingSpaces++;
		super.visitCtAssignment(assignment);

		CtExpression<?> assignee = assignment.getAssigned();
		CtExpression<?> value = assignment.getAssignment();

		// Variable Assignment - CheckVarAssign
		if (assignee instanceof CtVariableWriteImpl){
			CtVariableWriteImpl<?> var = (CtVariableWriteImpl<?>) assignee;
        	// Γ; Δ; Σ; 𝜑 ⊢ 𝑒 ⇓ 𝜈 ⊣ Δ′; Σ′; 𝜑′
			SymbolicValue valueSV = getValueOrLog((SymbolicValue) value.getMetadata(EVAL_KEY), assignment, "Symbolic value for assignment value not found");

			// Δ′[𝑥 ↦→ 𝜈]; Σ′ ⪰ Δ′′; Σ′′
			symbEnv.addVarSymbolicValue(var.getVariable().getSimpleName(), valueSV);

		// Field Assignment - CheckFieldAssign
		} else if (assignee instanceof CtFieldWrite){
			CtFieldWrite<?> fieldWrite = (CtFieldWrite<?>) assignee;
			CtExpression<?> x = fieldWrite.getTarget();
			CtFieldReference<?> f = fieldWrite.getVariable();

			// field(Γ(𝑥), 𝑓) = 𝛼 𝐶
			UniquenessAnnotation fieldPerm = getValueOrLog(maps.getFieldAnnotation(f.getSimpleName(), x.getType()), assignment, "Field annotation not found for " + f.getSimpleName());
	
        	// Γ; Δ; Σ; 𝜑 ⊢ 𝑒 ⇓ 𝜈′ ⊣ Δ′; Σ′; 𝜑′
			SymbolicValue vv = getValueOrLog((SymbolicValue) value.getMetadata(EVAL_KEY), assignment, "Symbolic value for field assignment value not found");

			// Γ; Δ′; Σ′; 𝜑′ ⊢ 𝑥 ⇓ 𝜈 ⊣ Δ′′; Σ′′; 𝜑′′
			SymbolicValue v = getValueOrLog((SymbolicValue) x.getMetadata(EVAL_KEY), assignment, "Symbolic value for field receiver not found");

			// Σ′′ ⊢ 𝜈′ : 𝛼 ⊣ Σ′′′
			UniquenessAnnotation vvPerm = permEnv.get(vv);
			if (!permEnv.usePermissionAs(vv, vvPerm, fieldPerm)) logError(String.format("Expected %s but got %s", fieldPerm, vvPerm), assignment);

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
		CtConstructor<?> c = maps.getCtConstructor(klass, paramSize);
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
	 */
	@Override
	public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
		logInfo("Visiting binary operator <"+ operator.toStringDebug()+">", operator);
		loggingSpaces++;
		super.visitCtBinaryOperator(operator);

		SymbolicValue left = getValueOrLog((SymbolicValue) operator.getLeftHandOperand().getMetadata(EVAL_KEY), operator, "Symbolic value for left binary operand not found");
		SymbolicValue right = getValueOrLog((SymbolicValue) operator.getRightHandOperand().getMetadata(EVAL_KEY), operator, "Symbolic value for right binary operand not found");
		BinaryOperator translatedOperator = SpoonOperatorTranslator.toRj(operator.getKind());
		if (translatedOperator == null) {
			logError("Unsupported binary operator in evaluation: " + operator.getKind(), operator);
			return;
		}
		SymbolicValue sv = evaluator.evalBinary(left, translatedOperator, right);

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

		UnaryOperator translatedOperator = SpoonOperatorTranslator.toRj(operator.getKind());
		if (translatedOperator == null) {
			// Increment and decrement are Java operations outside the refinement grammar.
			SymbolicValue sv = symbEnv.getFresh();
			permEnv.add(sv, new UniquenessAnnotation(Uniqueness.SHARED));
			operator.putMetadata(EVAL_KEY, sv);
			loggingSpaces--;
			return;
		}

		SymbolicValue operand = getValueOrLog((SymbolicValue) operator.getOperand().getMetadata(EVAL_KEY), operator, "Symbolic value for unary operand not found");
		SymbolicValue sv = evaluator.evalUnary(translatedOperator, operand);
		
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

	@Override
	public <T> void visitCtVariableRead(CtVariableRead<T> variableRead) {
		loggingSpaces++;
		logInfo("Visiting variable read <"+ variableRead.toString()+">", variableRead);
		super.visitCtVariableRead(variableRead);

		SymbolicValue sv = symbEnv.get(variableRead.getVariable().getSimpleName());
		variableRead.putMetadata(EVAL_KEY, sv);
		logInfo(variableRead.toString() + ": "+ sv);
		loggingSpaces--;
	}

	@Override
	public <T> void visitCtThisAccess(CtThisAccess<T> thisAccess) {
		super.visitCtThisAccess(thisAccess);

		SymbolicValue sv = symbEnv.get(THIS);
		thisAccess.putMetadata(EVAL_KEY, sv);
		logInfo(thisAccess.toString() + ": " + sv);
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

	private <T> T getValueOrLog(T value, CtElement element, String message, Object... args) {
		if (value == null) {
			logError(String.format(message, args), element);
		}
		return value;
	}

}
