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
import rj_language.ast.Var;
import rj_language.parsing.RefinementsParser;
import rj_language.visitors.ExpressionPrettyPrinter;
import typechecking.Evaluator;

public class EvaluatorBindingRulesTest extends EvaluatorBaseTest {
	@Nested
	class EvalVar {
		@Test
		void substitutesVariableWithSymbolicValue() throws Exception {
			SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
			Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

			Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x"));

			assertEquals(x.toString(), ExpressionPrettyPrinter.print(result));
		}
	
		@Test
		void rejectsUnknownVariable() {
			Evaluator missingEvaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> missingEvaluator.evalPredicate(RefinementsParser.createAST("missing")));
			assertEquals("Unknown symbolic value for variable missing", ex.getMessage());
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

			Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected"));

			assertEquals(field.toString(), ExpressionPrettyPrinter.print(result));
		}

		@Test
		void createsFreshFieldForExclusiveReceiver() throws Exception {
			SymbolicValue x = addVariable("x", Uniqueness.FREE);
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected"));

			SymbolicValue field = symbEnv.get(x, "isConnected");
			assertEquals(field.toString(), ExpressionPrettyPrinter.print(result));
			assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(field));
		}

		@Test
		void rejectsSharedReceiverForNonSharedField() throws Exception {
			addVariable("x", Uniqueness.SHARED);
			Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

			Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected")));
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
			result = evaluator.evalPredicate(connectPrecondition);
		}

		@Test
		void flattensToSingleSymbolicValue() {
			assertInstanceOf(Var.class, result);
			assertTrue(ExpressionPrettyPrinter.print(result).matches("𝜈\\d+"));
		}

		@Test
		void recordsAllElevenConjuncts() {
			// 4 EvalConst (false x4) + 4 EvalBinary (==) + 3 EvalBinary (&&)
			assertEquals(11, refinementPath.path.size());
		}

		@Test
		void assignsImmutablePermissionToEachField() {
			for (String field : List.of("isConnected", "isClosed")) {
				assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(symbEnv.get(thisValue, field)));
				assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(symbEnv.get(sink, field)));
			}
		}

		@Test
		void eachFieldAppearsExactlyOnceInConjunct() {
			SymbolicValue thisConnected = symbEnv.get(thisValue, "isConnected");
			SymbolicValue thisClosed = symbEnv.get(thisValue, "isClosed");
			SymbolicValue sinkConnected = symbEnv.get(sink, "isConnected");
			SymbolicValue sinkClosed = symbEnv.get(sink, "isClosed");

			String conjunct = ExpressionPrettyPrinter.print(refinementPath.toConjunct());
			assertEquals(1, countOccurrences(conjunct, thisConnected.toString()));
			assertEquals(1, countOccurrences(conjunct, thisClosed.toString()));
			assertEquals(1, countOccurrences(conjunct, sinkConnected.toString()));
			assertEquals(1, countOccurrences(conjunct, sinkClosed.toString()));
		}
	}
}
