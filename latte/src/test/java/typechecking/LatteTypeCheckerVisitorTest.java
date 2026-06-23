package typechecking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.reference.CtLocalVariableReference;

class LatteTypeCheckerVisitorTest extends LatteTypeCheckerIntegrationTestBase {
	@Nested
	class LiteralVisitor {
		@Test
		void supportedLiteralsCreateImmutableValuesAndPathConditions() {
			assertLiteral(42, "42");
			assertLiteral(true, "true");
			assertLiteral(3.5, "3.5");
			assertLiteral("latte", "\"latte\"");
		}

		@Test
		void nullCreatesFreeValueWithoutPathCondition() {
			CtLiteral<?> nullLiteral = elements(CtLiteral.class).stream()
				.filter(literal -> literal.getValue() == null)
				.findFirst()
				.orElseThrow();
			SymbolicValue nullValue = symbolicValue(nullLiteral);

			assertEquals(
				new UniquenessAnnotation(Uniqueness.FREE),
				checker.nodePermissions.get(nullLiteral));
			assertTrue(
				printedPath().stream()
					.noneMatch(expression ->
						expression.startsWith(nullValue + " == ")));
		}
	}

	@Nested
	class UnaryOperatorVisitor {
		@Test
		void negationCreatesImmutableResultAndPathCondition() {
			assertUnary(UnaryOperatorKind.NEG, "-");
		}

		@Test
		void logicalNotCreatesImmutableResultAndPathCondition() {
			assertUnary(UnaryOperatorKind.NOT, "!");
		}
	}

	@Nested
	class BinaryOperatorVisitor {
		@Test
		void arithmeticCreatesImmutableResultAndPathCondition() {
			assertBinary(BinaryOperatorKind.PLUS, "+");
		}

		@Test
		void comparisonCreatesImmutableResultAndPathCondition() {
			assertBinary(BinaryOperatorKind.LT, "<");
		}

		@Test
		void logicalOperationCreatesImmutableResultAndPathCondition() {
			assertBinary(BinaryOperatorKind.AND, "&&");
		}
	}

	@Nested
	class VariableAccessVisitors {
		@Test
		void variableReadsReuseAccessibleSymbolicValues() {
			assertReadHasPermission("parameter", Uniqueness.IMMUTABLE);
			assertReadHasPermission("local", Uniqueness.IMMUTABLE);
		}

		@Test
		void localVariableReferenceReceivesSymbolicMetadata() {
			CtLocalVariableReference<?> reference =
				elements(CtLocalVariableReference.class).stream()
					.filter(candidate ->
						candidate.getSimpleName().equals("local"))
					.findFirst()
					.orElseThrow();

			symbolicValue(reference);
		}

		@Test
		void thisAccessReusesBorrowedReceiver() {
			CtThisAccess<?> thisAccess = elements(CtThisAccess.class).stream()
				.filter(access -> access.getParent(CtReturn.class) != null)
				.findFirst()
				.orElseThrow();

			symbolicValue(thisAccess);
			assertEquals(
				new UniquenessAnnotation(Uniqueness.BORROWED),
				checker.nodePermissions.get(thisAccess));
		}
	}

	@Nested
	class CatchVisitor {
		@Test
		void registersExceptionAsBorrowed() {
			CtCatch catchBlock = elements(CtCatch.class).stream()
				.findFirst()
				.orElseThrow();
			String variableName = catchBlock.getParameter().getSimpleName();

			assertEquals(
				new UniquenessAnnotation(Uniqueness.BORROWED),
				checker.catchPermissions.get(variableName));
			assertReadHasPermission(variableName, Uniqueness.BORROWED);
		}
	}
}
