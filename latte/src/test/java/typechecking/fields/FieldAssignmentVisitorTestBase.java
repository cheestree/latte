package typechecking.fields;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.UniquenessAnnotation;
import helpers.RecordingTypeChecker;
import helpers.RecordingTypeChecker.AssignmentState;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import typechecking.LatteClassFirstPass;
import typechecking.RefinementFirstPass;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class FieldAssignmentVisitorTestBase {
	protected static final String EVAL_KEY = "symbolic_value";

	protected static final String FIXTURE_DIRECTORY = "src/test/examples/typechecking/fields/";
	private static final String FIXTURE = FIXTURE_DIRECTORY + "FieldAssignmentVisitorCases.java";
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

		RecordingTypeChecker recordingChecker = new RecordingTypeChecker(
			typeEnv,
			symbEnv,
			permEnv,
			maps,
			refinementPath);
		processedModel.getRootPackage().accept(recordingChecker);
		return processedModel;
	}

	protected CtMethod<?> methodNamed(String methodName) {
		return model.getElements(new TypeFilter<>(CtMethod.class)).stream()
			.filter(method -> method.getSimpleName().equals(methodName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing fixture method: " + methodName));
	}

	protected List<CtFieldRead> fieldReads(String fieldName) {
		return methodNamed("readFields")
			.getElements(new TypeFilter<>(CtFieldRead.class))
			.stream()
			.filter(read -> read.getVariable().getSimpleName().equals(fieldName))
			.toList();
	}

	protected CtFieldRead<?> fieldRead(String fieldName) {
		return fieldReads(fieldName).stream()
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing fixture field read: " + fieldName));
	}

	protected CtAssignment<?, ?> assignmentIn(String methodName) {
		return methodNamed(methodName)
			.getElements(new TypeFilter<>(CtAssignment.class))
			.stream()
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing fixture assignment in: " + methodName));
	}

	protected AssignmentState assignmentState(String methodName) {
		CtAssignment<?, ?> assignment = assignmentIn(methodName);
		AssignmentState state = RecordingTypeChecker.assignmentState(assignment);
		assertNotNull(state, () -> "Missing recorded assignment state in " + methodName);
		return state;
	}

	protected UniquenessAnnotation fieldReadPermission(CtFieldRead<?> read) {
		UniquenessAnnotation permission =
			(UniquenessAnnotation) read.getMetadata(
				RecordingTypeChecker.FIELD_READ_PERMISSION_KEY);
		assertNotNull(permission, () -> "Missing recorded field permission on " + read);
		return permission;
	}

	protected SymbolicValue symbolicValue(CtElement element) {
		SymbolicValue value = (SymbolicValue) element.getMetadata(EVAL_KEY);
		assertNotNull(value, () -> "Missing symbolic metadata on " + element);
		return value;
	}

}
