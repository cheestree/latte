package helpers;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.UniquenessAnnotation;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtVariableWrite;
import typechecking.LatteTypeChecker;

public class RecordingTypeChecker extends LatteTypeChecker {
	private static final String EVAL_KEY = "symbolic_value";
	public static final String FIELD_READ_PERMISSION_KEY = "test_field_permission";
	private static final String ASSIGNMENT_STATE_KEY = "test_assignment_state";

	protected final TypeEnvironment typeEnvironment;
	protected final SymbolicEnvironment symbolicEnvironment;
	protected final PermissionEnvironment permissionEnvironment;

	public RecordingTypeChecker(
		TypeEnvironment typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		ClassLevelMaps maps,
		RefinementPath refinementPath) {
		super(typeEnv, symbEnv, permEnv, maps, refinementPath);
		typeEnvironment = typeEnv;
		symbolicEnvironment = symbEnv;
		permissionEnvironment = permEnv;
	}

	public static AssignmentState assignmentState(
		CtAssignment<?, ?> assignment) {
		return (AssignmentState) assignment.getMetadata(ASSIGNMENT_STATE_KEY);
	}

	@Override
	public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
		super.visitCtFieldRead(fieldRead);

		SymbolicValue value =
			(SymbolicValue) fieldRead.getMetadata(EVAL_KEY);
		if (value != null) {
			fieldRead.putMetadata(
				FIELD_READ_PERMISSION_KEY,
				permissionEnvironment.get(value));
		}
	}

	@Override
	public <T, A extends T> void visitCtAssignment(
		CtAssignment<T, A> assignment) {
		super.visitCtAssignment(assignment);

		SymbolicValue rightHandValue = (SymbolicValue) assignment.getAssignment().getMetadata(EVAL_KEY);
		SymbolicValue storedValue = storedValue(assignment);
		if (storedValue != null) {
			assignment.putMetadata(
				ASSIGNMENT_STATE_KEY,
				new AssignmentState(
					rightHandValue,
					storedValue,
					permissionEnvironment.get(storedValue)));
		}
	}

	private SymbolicValue storedValue(CtAssignment<?, ?> assignment) {
		if (assignment.getAssigned() instanceof CtFieldWrite<?> fieldWrite) {
			SymbolicValue receiver =
				(SymbolicValue) fieldWrite.getTarget().getMetadata(EVAL_KEY);
			return symbolicEnvironment.get(receiver, fieldWrite.getVariable().getSimpleName());
		}

		if (assignment.getAssigned() instanceof CtVariableWrite<?> variableWrite) {
			return symbolicEnvironment.get(variableWrite.getVariable().getSimpleName());
		}

		return null;
	}

	public record AssignmentState(
		SymbolicValue rightHandValue,
		SymbolicValue storedValue,
		UniquenessAnnotation storedPermission) {
	}
}
