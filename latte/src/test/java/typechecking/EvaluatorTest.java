package typechecking;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementContract;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.Expression;
import rj_language.ast.Var;
import rj_language.parsing.RefinementsParser;
import rj_language.visitors.ExpressionPrettyPrinter;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class EvaluatorTest {
	private static ClassLevelMaps maps;
	private static CtTypeReference<?> writerType;
	private static CtTypeReference<?> readerType;
	private static Expression connectPrecondition;
	private SymbolicEnvironment symbEnv;
	private PermissionEnvironment permEnv;
	private RefinementPath refinementPath;

	@BeforeAll
	static void loadFieldMetadata() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new File("./src/test/examples/refinements/PipedOutputStreamCorrect.java").getAbsolutePath());
		CtModel model = launcher.buildModel();

		maps = new ClassLevelMaps();
		model.getRootPackage().accept(new LatteClassFirstPass(
			new SymbolicEnvironment(),
			new PermissionEnvironment(),
			maps));
		model.getRootPackage().accept(new RefinementFirstPass(
			new SymbolicEnvironment(),
			new PermissionEnvironment(),
			maps));

		CtClass<?> writerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
			.filter(c -> c.getSimpleName().equals("PipedOutputStream"))
			.findFirst()
			.orElseThrow();
		writerType = writerClass.getReference();

		CtClass<?> readerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
			.filter(c -> c.getSimpleName().equals("PipedInputStream"))
			.findFirst()
			.orElseThrow();
		readerType = readerClass.getReference();

		RefinementContract connectContract = maps.getMethodContract(writerClass, "connect", 1);
		connectPrecondition = connectContract.getFrom();
	}

	@BeforeEach
	void setEnvironment() {
		symbEnv = new SymbolicEnvironment();
		permEnv = new PermissionEnvironment();
		refinementPath = new RefinementPath();
		symbEnv.enterScope();
		permEnv.enterScope();
	}

	@AfterEach
	void tearDownEnvironment() {
		refinementPath = null;
		permEnv.exitScope();
		symbEnv.exitScope();
	}

	@Test
	void evalPredicateReturnsNullForNullPredicate() {
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);
		Expression result = evaluator.evalPredicate(null);
		assertEquals(null, result);
	}

	@Test
	void predConstCreatesFreshImmutableValueAndPathCondition() throws Exception {
		Expression literal = RefinementsParser.createAST("true");
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(literal);

		assertEquals("𝜈0", ExpressionPrettyPrinter.print(result));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(new SymbolicValue(0)));
		assertEquals("𝜈0 == true", ExpressionPrettyPrinter.print(refinementPath.toConjunct()));
	}

	@Test
	void evalConstCreatesFreshImmutableValueAndPathCondition() throws Exception {
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("42"));

		assertEquals("𝜈0", ExpressionPrettyPrinter.print(result));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(new SymbolicValue(0)));
		assertEquals("𝜈0 == 42", ExpressionPrettyPrinter.print(refinementPath.toConjunct()));
	}

	@Test
	void evalBinaryCreatesFreshImmutableValueAndPathCondition() throws Exception {
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("1 + 2"));

		assertEquals("𝜈2", ExpressionPrettyPrinter.print(result));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(new SymbolicValue(2)));
		assertEquals("𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 + 𝜈1", ExpressionPrettyPrinter.print(refinementPath.toConjunct()));
	}

	@Test
	void predVarSubstitutesVariableWithSymbolicValue() throws Exception {
		SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x"));

		assertEquals(x.toString(), ExpressionPrettyPrinter.print(result));
	}

	@Test
	void predFieldUsesExistingTrackedFieldValue() throws Exception {
		SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
		SymbolicValue field = symbEnv.addField(x, "isConnected");
		permEnv.add(field, new UniquenessAnnotation(Uniqueness.IMMUTABLE));
		Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected"));

		assertEquals(field.toString(), ExpressionPrettyPrinter.print(result));
	}

	@Test
	void predFieldCreatesFreshFieldForExclusiveReceiver() throws Exception {
		SymbolicValue x = addVariable("x", Uniqueness.FREE);
		Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected"));

		SymbolicValue field = symbEnv.get(x, "isConnected");
		assertEquals(field.toString(), ExpressionPrettyPrinter.print(result));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(field));
	}

	@Test
	void predComplexContractPreconditionUsesFixtureMetadata() throws Exception {
		SymbolicValue thisValue = addVariable("this", Uniqueness.BORROWED);
		SymbolicValue sink = addVariable("sink", Uniqueness.BORROWED);
		Evaluator evaluator = new Evaluator(maps, Map.of("this", writerType, "sink", readerType), symbEnv, permEnv, refinementPath);

		Expression result = evaluator.evalPredicate(connectPrecondition);

		SymbolicValue thisConnected = symbEnv.get(thisValue, "isConnected");
		SymbolicValue thisClosed = symbEnv.get(thisValue, "isClosed");
		SymbolicValue sinkConnected = symbEnv.get(sink, "isConnected");
		SymbolicValue sinkClosed = symbEnv.get(sink, "isClosed");

		// Result is a single flattened immutable symbolic value.
		assertTrue(result instanceof Var);
		String printed = ExpressionPrettyPrinter.print(result);
		assertTrue(printed.matches("𝜈\\d+"));

		// 4 EvalConst (false x4) + 4 EvalBinary (==) + 3 EvalBinary (&&) = 11 φ conjuncts.
		assertEquals(11, refinementPath.path.size());

		// Each field introduced exactly once, with its declared (IMMUTABLE) permission.
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(thisConnected));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(thisClosed));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(sinkConnected));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(sinkClosed));

		// Each field's symbolic value appears as an operand of exactly one equality conjunct.
		String conjunct = ExpressionPrettyPrinter.print(refinementPath.toConjunct());
		assertEquals(1, countOccurrences(conjunct, thisConnected.toString()));
		assertEquals(1, countOccurrences(conjunct, thisClosed.toString()));
		assertEquals(1, countOccurrences(conjunct, sinkConnected.toString()));
		assertEquals(1, countOccurrences(conjunct, sinkClosed.toString()));
	}
	
	@Test
	void predVarRejectsUnknownVariable() {
		Evaluator missingEvaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv, refinementPath);

		Throwable ex = assertThrows(IllegalStateException.class, () -> missingEvaluator.evalPredicate(RefinementsParser.createAST("missing")));
		assertEquals("Unknown symbolic value for variable missing", ex.getMessage());
	}

	@Test
	void predFieldRejectsSharedReceiverForNonSharedField() throws Exception {
		addVariable("x", Uniqueness.SHARED);
		Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv, refinementPath);

		Throwable ex = assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected")));
		assertEquals("Receiver x with permission SHARED cannot access non-shared field isConnected", ex.getMessage());
	}

	private SymbolicValue addVariable(String name, Uniqueness permission) {
		SymbolicValue value = symbEnv.addVariable(name);
		permEnv.add(value, new UniquenessAnnotation(permission));
		return value;
	}

	private static long countOccurrences(String haystack, String needle) {
		return Arrays.stream(haystack.split(needle, -1)).count() - 1;
	}
}
