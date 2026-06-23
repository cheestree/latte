package helpers;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.UniquenessAnnotation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import typechecking.LatteTypeChecker;

public class ContractRecordingTypeChecker extends LatteTypeChecker {
	public static final String PARAMETER_STATE_KEY = "test_parameter_state";
	public static final String RETURN_STATE_KEY = "test_return_state";
	public static final String METHOD_PATH_ADDITIONS_KEY = "test_method_path_additions";

	protected final TypeEnvironment typeEnvironment;
	protected final SymbolicEnvironment symbolicEnvironment;
	protected final PermissionEnvironment permissionEnvironment;
	protected final RefinementPath refinementPath;

	public ContractRecordingTypeChecker(
		TypeEnvironment typeEnv,
		SymbolicEnvironment symbEnv,
		PermissionEnvironment permEnv,
		ClassLevelMaps maps,
		RefinementPath refPath) {
		super(typeEnv, symbEnv, permEnv, maps, refPath);
		typeEnvironment = typeEnv;
		symbolicEnvironment = symbEnv;
		permissionEnvironment = permEnv;
		refinementPath = refPath;
	}

	@Override
	public <T> void visitCtMethod(CtMethod<T> method) {
		int initialPathSize = refinementPath.path.size();
		super.visitCtMethod(method);
		method.putMetadata(METHOD_PATH_ADDITIONS_KEY, refinementPath.path.size() - initialPathSize);
	}

	@Override
	public <T> void visitCtParameter(CtParameter<T> parameter) {
		super.visitCtParameter(parameter);

		SymbolicValue parameterValue = symbolicEnvironment.get(parameter.getSimpleName());
		SymbolicValue thisValue = symbolicEnvironment.get("this");
		parameter.putMetadata(
			PARAMETER_STATE_KEY,
			new ParameterState(
				typeEnvironment.get(parameter.getSimpleName()),
				parameterValue,
				permissionEnvironment.get(parameterValue),
				typeEnvironment.get("this"),
				thisValue,
				permissionEnvironment.get(thisValue)
			)
		);
	}

	@Override
	public <R> void visitCtReturn(CtReturn<R> returnStatement) {
		super.visitCtReturn(returnStatement);

		SymbolicValue returnedValue = (SymbolicValue) returnStatement.getReturnedExpression().getMetadata("symbolic_value");
		
		returnStatement.putMetadata(RETURN_STATE_KEY,
			new ReturnState(
				returnedValue,
				permissionEnvironment.get(returnedValue)
			)
		);
	}

	public record ParameterState(
		CtTypeReference<?> parameterType,
		SymbolicValue parameterValue,
		UniquenessAnnotation parameterPermission,
		CtTypeReference<?> thisType,
		SymbolicValue thisValue,
		UniquenessAnnotation thisPermission) {
	}

	public record ReturnState(
		SymbolicValue returnedValue,
		UniquenessAnnotation returnedPermission) {
	}
}
