package typechecking.fields;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import context.Uniqueness;
import context.UniquenessAnnotation;
import helpers.RecordingTypeChecker.AssignmentState;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import typechecking.LatteException;

class LatteTypeCheckerFieldAssignmentVisitorTest extends FieldAssignmentVisitorTestBase {
	@Nested
	class FieldReadVisitor {
		@Test
		void exclusiveReceiverCreatesFieldWithDeclaredPermission() {
			CtFieldRead<?> read = fieldRead("uniqueField");

			symbolicValue(read);
			assertEquals(new UniquenessAnnotation(Uniqueness.UNIQUE), fieldReadPermission(read));
		}

		@Test
		void repeatedReadReusesTrackedFieldValue() {
			List<CtFieldRead> reads = fieldReads("uniqueField");

			assertEquals(2, reads.size());
			assertSame(symbolicValue(reads.get(0)), symbolicValue(reads.get(1)));
		}

		@Test
		void sharedReceiverCanReadSharedField() {
			CtFieldRead<?> read = fieldRead("sharedField");

			assertEquals(new UniquenessAnnotation(Uniqueness.SHARED), fieldReadPermission(read));
		}

		@Test
		void staticFieldUsesConservativeSharedPermission() {
			CtFieldRead<?> read = fieldRead("out");

			assertEquals(new UniquenessAnnotation(Uniqueness.SHARED), fieldReadPermission(read));
		}

		@Test
		void sharedReceiverCannotReadUniqueField() {
			LatteException error = assertThrows(
				LatteException.class,
				() -> process(FIXTURE_DIRECTORY + "InvalidSharedFieldRead.java"));

			assertEquals("Receiver other with permission SHARED cannot access non-shared field uniqueField", error.getMessage());
		}
	}

	@Nested
	class FieldWriteVisitor {
		@Test
		void attachesReceiverSymbolicValueToTarget() {
			CtAssignment<?, ?> assignment = assignmentIn("fieldAssignment");
			CtFieldWrite<?> write = (CtFieldWrite<?>) assignment.getAssigned();

			symbolicValue(write.getTarget());
		}
	}

	@Nested
	class AssignmentVisitor {
		@Test
		void variableAssignmentStoresRightHandSymbolicValue() {
			AssignmentState state = assignmentState("variableAssignment");

			assertEquals(state.rightHandValue(), state.storedValue());
		}

		@Test
		void fieldAssignmentStoresRightHandValueAtReceiverField() {
			AssignmentState state = assignmentState("fieldAssignment");

			assertEquals(state.rightHandValue(), state.storedValue());
			assertEquals(new UniquenessAnnotation(Uniqueness.UNIQUE), state.storedPermission());
		}
	}
}
