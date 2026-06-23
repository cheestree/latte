package typechecking;

import java.io.File;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.visitors.ExpressionPrettyPrinter;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

abstract class LatteTypeCheckerIntegrationTestBase {
	protected static final String EVAL_KEY = "symbolic_value";

	private static final String FIXTURE = "src/test/examples/typechecking/ExpressionVisitorCases.java";

	protected CtModel model;
	protected RefinementPath refinementPath;
	protected RecordingTypeChecker checker;

	@BeforeEach
	void processFixture() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new File(FIXTURE).getAbsolutePath());
		model = launcher.buildModel();

		TypeEnvironment typeEnv = new TypeEnvironment();
		SymbolicEnvironment symbEnv = new SymbolicEnvironment();
		PermissionEnvironment permEnv = new PermissionEnvironment();
		refinementPath = new RefinementPath();
		ClassLevelMaps maps = new ClassLevelMaps();

		model.getRootPackage().accept(new LatteClassFirstPass(typeEnv, symbEnv, permEnv, maps));
		model.getRootPackage().accept(new RefinementFirstPass(typeEnv, symbEnv, permEnv, maps));

		checker = new RecordingTypeChecker(typeEnv, symbEnv, permEnv, maps, refinementPath);
		model.getRootPackage().accept(checker);
	}

	protected void assertLiteral(Object literalValue, String printedLiteral) {
		CtLiteral<?> literal = elements(CtLiteral.class).stream()
			.filter(candidate -> literalValue.equals(candidate.getValue()))
			.findFirst()
			.orElseThrow();
		SymbolicValue value = symbolicValue(literal);

		assertPermission(value, Uniqueness.IMMUTABLE);
		assertTrue(printedPath().contains(value + " == " + printedLiteral));
	}

	protected void assertUnary(UnaryOperatorKind kind, String symbol) {
		CtUnaryOperator<?> operator = elements(CtUnaryOperator.class).stream()
			.filter(candidate -> candidate.getKind() == kind)
			.findFirst()
			.orElseThrow();
		SymbolicValue operand = symbolicValue(operator.getOperand());
		SymbolicValue result = symbolicValue(operator);

		assertPermission(result, Uniqueness.IMMUTABLE);
		assertTrue(printedPath().contains(result + " == " + symbol + operand));
	}

	protected void assertBinary(BinaryOperatorKind kind, String symbol) {
		CtBinaryOperator<?> operator = elements(CtBinaryOperator.class).stream()
			.filter(candidate -> candidate.getKind() == kind)
			.findFirst()
			.orElseThrow();
		SymbolicValue left = symbolicValue(operator.getLeftHandOperand());
		SymbolicValue right = symbolicValue(operator.getRightHandOperand());
		SymbolicValue result = symbolicValue(operator);

		assertPermission(result, Uniqueness.IMMUTABLE);
		assertTrue(printedPath().contains(result + " == " + left + " " + symbol + " " + right));
	}

	protected void assertReadHasPermission(
		String variableName,
		Uniqueness permission) {
		CtVariableRead<?> read = elements(CtVariableRead.class).stream()
			.filter(candidate ->
				candidate.getVariable().getSimpleName().equals(variableName))
			.findFirst()
			.orElseThrow();

		symbolicValue(read);
		assertEquals(new UniquenessAnnotation(permission), checker.nodePermissions.get(read));
	}

	protected <T extends CtElement> List<T> elements(Class<T> elementType) {
		return model.getElements(new TypeFilter<>(elementType));
	}

	protected SymbolicValue symbolicValue(CtElement element) {
		SymbolicValue value = (SymbolicValue) element.getMetadata(EVAL_KEY);
		assertNotNull(value);
		return value;
	}

	protected void assertPermission(
		SymbolicValue value,
		Uniqueness permission) {
		assertEquals(new UniquenessAnnotation(permission), checker.valuePermissions.get(value));
	}

	protected List<String> printedPath() {
		return refinementPath.getPath().stream().map(ExpressionPrettyPrinter::print).toList();
	}

	protected static final class RecordingTypeChecker extends LatteTypeChecker {
		final Map<CtElement, UniquenessAnnotation> nodePermissions = new IdentityHashMap<>();
		final Map<SymbolicValue, UniquenessAnnotation> valuePermissions = new HashMap<>();
		final Map<String, UniquenessAnnotation> catchPermissions = new HashMap<>();

		private RecordingTypeChecker(
			TypeEnvironment typeEnv,
			SymbolicEnvironment symbEnv,
			PermissionEnvironment permEnv,
			ClassLevelMaps maps,
			RefinementPath refinementPath) {
			super(typeEnv, symbEnv, permEnv, maps, refinementPath);
		}

		@Override
		public <T> void visitCtLiteral(CtLiteral<T> literal) {
			super.visitCtLiteral(literal);
			record(literal);
		}

		@Override
		public <T> void visitCtUnaryOperator(CtUnaryOperator<T> operator) {
			super.visitCtUnaryOperator(operator);
			record(operator);
		}

		@Override
		public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
			super.visitCtBinaryOperator(operator);
			record(operator);
		}

		@Override
		public <T> void visitCtVariableRead(CtVariableRead<T> variableRead) {
			super.visitCtVariableRead(variableRead);
			record(variableRead);
		}

		@Override
		public <T> void visitCtThisAccess(CtThisAccess<T> thisAccess) {
			super.visitCtThisAccess(thisAccess);
			record(thisAccess);
		}

		@Override
		public void visitCtCatch(CtCatch catchBlock) {
			super.visitCtCatch(catchBlock);
			String variableName = catchBlock.getParameter().getSimpleName();
			SymbolicValue value = symbEnv.get(variableName);
			catchPermissions.put(variableName, permEnv.get(value));
		}

		private void record(CtElement element) {
			SymbolicValue value = (SymbolicValue) element.getMetadata(EVAL_KEY);
			if (value == null) {
				return;
			}

			UniquenessAnnotation permission = permEnv.get(value);
			nodePermissions.put(element, permission);
			valuePermissions.put(value, permission);
		}
	}
}
