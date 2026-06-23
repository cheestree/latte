package typechecking.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import context.Uniqueness;
import context.UniquenessAnnotation;
import helpers.ContractRecordingTypeChecker;
import helpers.ContractRecordingTypeChecker.ParameterState;
import helpers.ContractRecordingTypeChecker.ReturnState;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import typechecking.LatteException;

class LatteTypeCheckerContractVisitorTest extends ContractVisitorTestBase {
	@Nested
	class ConstructorVisitor {
		@Test
		void initializesThisAndConstructorParameterEnvironments() {
			CtConstructor<?> constructor = constructor();
			ParameterState state = parameterState(constructor.getParameters().get(0));

			assertEquals("ContractVisitorCases", state.thisType().getSimpleName());
			assertNotNull(state.thisValue());
			assertEquals(new UniquenessAnnotation(Uniqueness.BORROWED), state.thisPermission());
			assertEquals(new UniquenessAnnotation(Uniqueness.SHARED), state.parameterPermission());
		}
	}

	@Nested
	class MethodVisitor {
		@Test
		void initializesThisAndMethodParameterEnvironments() {
			CtMethod<?> method = methodNamed("echo");
			CtParameter<?> parameter = method.getParameters().get(0);
			ParameterState state = parameterState(parameter);

			assertEquals(parameter.getType(), state.parameterType());
			assertNotNull(state.parameterValue());
			assertEquals(new UniquenessAnnotation(Uniqueness.SHARED), state.parameterPermission());
			assertEquals(method.getDeclaringType().getReference(), state.thisType());
		}

		@Test
		void evaluatesAndAssumesMethodPrecondition() {
			CtMethod<?> method = methodNamed("echo");

			assertEquals(2, method.getMetadata(ContractRecordingTypeChecker.METHOD_PATH_ADDITIONS_KEY));
		}
	}

	@Nested
	class ReturnVisitor {
		@Test
		void checksReturnedValueAgainstDeclaredPermission() {
			ReturnState state = returnState(methodNamed("echo"));

			assertNotNull(state.returnedValue());
			assertEquals(new UniquenessAnnotation(Uniqueness.SHARED), state.returnedPermission());
		}

		@Test
		void rejectsReturnExpressionWithIncompatibleType() {
			LatteException error = assertThrows(
				LatteException.class,
				() -> process(FIXTURE_DIRECTORY + "InvalidReturnType.java"));

			assertEquals("Return type mismatch: expected typechecking.contracts.InvalidReturnType.Expected" + " but got typechecking.contracts.InvalidReturnType.Actual", error.getMessage());
		}
	}
}
