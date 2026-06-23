package evaluator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.Expression;
import rj_language.ast.FieldAccess;
import rj_language.ast.Var;
import rj_language.parsing.RefinementsParser;
import typechecking.Evaluator;

public class EvaluatorBindingRulesTest extends EvaluatorBaseTest {
	@Nested
	class EvalVar {
		@Test
		void substitutesVariableWithSymbolicValue() throws Exception {
			SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
			Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

			Expression result = evaluator.eval(RefinementsParser.createAST("x"));

			assertExpressionEquals(result, x.toString());
		}
	
		@Test
		void rejectsUnknownVariable() {
			Evaluator missingEvaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> missingEvaluator.eval(RefinementsParser.createAST("missing")));
			assertEquals("Unknown symbolic value for variable missing", ex.getMessage());
		}

		@Test
		void rejectsMissingPermission() {
			symbEnv.addVariable("x");
			Evaluator missingEvaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> missingEvaluator.eval(RefinementsParser.createAST("x")));
			assertEquals("Missing permission for symbolic value 𝜈0 when evaluating variable x", ex.getMessage());
		}

		@Test
		void rejectsBottomPermission() {
			addVariable("x", Uniqueness.BORROWED);
			permEnv.add(symbEnv.get("x"), new UniquenessAnnotation(Uniqueness.BOTTOM));
			Evaluator missingEvaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> missingEvaluator.eval(RefinementsParser.createAST("x")));
			assertEquals("Variable is inaccessible in evaluation: x", ex.getMessage());
		}
	}

	@Nested
	class EvalField {
		@Test
		void usesExistingTrackedFieldValue() throws Exception {
			SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
			SymbolicValue field = symbEnv.addField(x, "isConnected");
			permEnv.add(field, new UniquenessAnnotation(Uniqueness.IMMUTABLE));
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Expression result = evaluator.eval(RefinementsParser.createAST("x.isConnected"));

			assertExpressionEquals(result, field.toString());
		}

		@Test
		void createsFreshFieldForExclusiveReceiver() throws Exception {
			SymbolicValue x = addVariable("x", Uniqueness.FREE);
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Expression result = evaluator.eval(RefinementsParser.createAST("x.isConnected"));

			SymbolicValue field = symbEnv.get(x, "isConnected");
			assertExpressionEquals(result, field.toString());
			assertImmutable(field);
		}

		@Test
		void rejectsNonVariableReceiver() {
			Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);
			Expression fieldAccess = new FieldAccess(new FieldAccess(new Var("x"), "inner"), "isConnected");

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(fieldAccess));
			assertTrue(ex.getMessage().startsWith("Only variable receivers are supported in evaluation: "));
		}

		@Test
		void rejectsUnknownReceiverVariable() {
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.isConnected")));
			assertEquals("Unknown symbolic value for variable x", ex.getMessage());
		}

		@Test
		void rejectsMissingReceiverPermission() throws Exception {
			symbEnv.addVariable("x");
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.isConnected")));
			assertEquals("Missing permission for symbolic value 𝜈0 when evaluating receiver x", ex.getMessage());
		}

		@Test
		void rejectsBottomReceiverPermission() throws Exception {
			addVariable("x", Uniqueness.BOTTOM);
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.isConnected")));
			assertEquals("Receiver is inaccessible in evaluation: x", ex.getMessage());
		}

		@Test
		void rejectsMissingTypeForUntrackedField() throws Exception {
			addVariable("x", Uniqueness.BORROWED);
			Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.isConnected")));
			assertEquals("Missing type for receiver x when evaluating x.isConnected", ex.getMessage());
		}

		@Test
		void rejectsUnknownFieldOnType() throws Exception {
			addVariable("x", Uniqueness.BORROWED);
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.unknown")));
			assertEquals("Unknown field unknown on type " + writerType, ex.getMessage());
		}

		@Test
		void rejectsBottomFieldPermission() throws Exception {
			SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
			SymbolicValue field = symbEnv.addField(x, "isConnected");
			permEnv.add(field, new UniquenessAnnotation(Uniqueness.BOTTOM));
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.isConnected")));
			assertEquals("Field is inaccessible in evaluation: x.isConnected", ex.getMessage());
		}

		@Test
		void rejectsSharedReceiverForNonSharedField() throws Exception {
			addVariable("x", Uniqueness.SHARED);
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.eval(RefinementsParser.createAST("x.isConnected")));
			assertEquals("Receiver x with permission SHARED cannot access non-shared field isConnected", ex.getMessage());
		}
	}

	@Nested
	class ComplexContractPrecondition {
		SymbolicValue thisValue, sink;
		Expression result;

		@BeforeEach
		void evaluate() throws Exception {
			thisValue = addVariable("this", Uniqueness.BORROWED);
			sink = addVariable("sink", Uniqueness.BORROWED);
			Evaluator evaluator = new Evaluator(maps, Map.of("this", writerType, "sink", readerType), symbEnv, permEnv, refinementPath);
			result = evaluator.eval(connectPrecondition);
		}

		@Test
		void flattensToSingleSymbolicValue() {
			assertInstanceOf(Var.class, result);
			assertExpressionMatches(result, "𝜈\\d+");
		}

		@Test
		void recordsAllElevenConjuncts() {
			// 4 EvalConst (false x4) + 4 EvalBinary (==) + 3 EvalBinary (&&)
			assertEquals(11, refinementPath.path.size());
		}

		@Test
		void assignsImmutablePermissionToEachField() {
			for (String field : List.of("isConnected", "isClosed")) {
				assertImmutable(symbEnv.get(thisValue, field));
				assertImmutable(symbEnv.get(sink, field));
			}
		}

		@Test
		void eachFieldAppearsExactlyOnceInConjunct() {
			SymbolicValue thisConnected = symbEnv.get(thisValue, "isConnected");
			SymbolicValue thisClosed = symbEnv.get(thisValue, "isClosed");
			SymbolicValue sinkConnected = symbEnv.get(sink, "isConnected");
			SymbolicValue sinkClosed = symbEnv.get(sink, "isClosed");

			Expression conjunct = refinementPath.toConjunct();
			assertEquals(1, countPrintedOccurrences(conjunct, thisConnected));
			assertEquals(1, countPrintedOccurrences(conjunct, thisClosed));
			assertEquals(1, countPrintedOccurrences(conjunct, sinkConnected));
			assertEquals(1, countPrintedOccurrences(conjunct, sinkClosed));
		}
	}
}
