package typechecking.contracts;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.TypeEnvironment;
import helpers.ContractRecordingTypeChecker;
import helpers.ContractRecordingTypeChecker.ParameterState;
import helpers.ContractRecordingTypeChecker.ReturnState;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.visitor.filter.TypeFilter;
import typechecking.LatteClassFirstPass;
import typechecking.RefinementFirstPass;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ContractVisitorTestBase {
	protected static final String FIXTURE_DIRECTORY = "src/test/examples/typechecking/contracts/";
	private static final String FIXTURE = FIXTURE_DIRECTORY + "ContractVisitorCases.java";

	protected CtModel model;

	@BeforeAll
	void processFixture() {
		model = process(FIXTURE);
	}

	protected CtModel process(String resourcePath) {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new File(resourcePath).getAbsolutePath());
		CtModel processedModel = launcher.buildModel();

		TypeEnvironment typeEnv = new TypeEnvironment();
		SymbolicEnvironment symbEnv = new SymbolicEnvironment();
		PermissionEnvironment permEnv = new PermissionEnvironment();
		RefinementPath refinementPath = new RefinementPath();
		ClassLevelMaps maps = new ClassLevelMaps();

		processedModel.getRootPackage().accept(new LatteClassFirstPass(typeEnv, symbEnv, permEnv, maps));
		processedModel.getRootPackage().accept(new RefinementFirstPass(typeEnv, symbEnv, permEnv, maps));
		processedModel.getRootPackage().accept(new ContractRecordingTypeChecker(typeEnv, symbEnv, permEnv, maps, refinementPath));
		return processedModel;
	}

	protected CtMethod<?> methodNamed(String methodName) {
		return model.getElements(new TypeFilter<>(CtMethod.class)).stream()
			.filter(method -> method.getSimpleName().equals(methodName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing fixture method: " + methodName));
	}

	protected CtConstructor<?> constructor() {
		return model.getElements(new TypeFilter<>(CtConstructor.class)).stream()
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing fixture constructor"));
	}

	protected ParameterState parameterState(CtParameter<?> parameter) {
		ParameterState state = (ParameterState) parameter.getMetadata(ContractRecordingTypeChecker.PARAMETER_STATE_KEY);
		assertNotNull(
			state,
			() -> "Missing parameter state for " + parameter.getSimpleName()
		);
		return state;
	}

	protected ReturnState returnState(CtMethod<?> method) {
		CtReturn<?> returnStatement = method
			.getElements(new TypeFilter<>(CtReturn.class))
			.stream()
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing return statement in " + method.getSimpleName()));
		ReturnState state = (ReturnState) returnStatement.getMetadata(ContractRecordingTypeChecker.RETURN_STATE_KEY);
		assertNotNull(
			state,
			() -> "Missing return state in " + method.getSimpleName()
		);
		return state;
	}
}
