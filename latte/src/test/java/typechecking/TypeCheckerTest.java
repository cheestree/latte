package typechecking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import api.App;

public class TypeCheckerTest {
	@Test
	void acceptsBorrowedConstructorAndThisFieldPreconditions() {
		assertDoesNotThrow(() ->
			App.launcher("src/test/java/typechecking/cases/TypeCheckerPreconditionPasses.java", false));
	}

	@Test
	void rejectsSharedVariableUsedInRefinementPrecondition() {
		LatteException ex = assertThrows(LatteException.class, () ->
			App.launcher("src/test/java/typechecking/cases/SharedVariableInPredicateError.java", false));

		assertTrue(ex.getMessage().contains("Predicate requires α > shared"));
	}

	@Test
	void rejectsUnknownVariableUsedInRefinementPrecondition() {
		LatteException ex = assertThrows(LatteException.class, () ->
			App.launcher("src/test/java/typechecking/cases/UnknownPreconditionError.java", false));

		assertTrue(ex.getMessage().contains("Unknown symbolic value for variable missing"));
	}

	@Test
	void rejectsNonSharedFieldReadThroughSharedReceiverInPrecondition() {
		LatteException ex = assertThrows(LatteException.class, () ->
			App.launcher("src/test/java/typechecking/cases/SharedReceiverUniqueFieldPredicateError.java", false));

		assertTrue(ex.getMessage().contains("cannot access non-shared field value"));
	}
}
