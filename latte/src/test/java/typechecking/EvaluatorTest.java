package typechecking;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementContract;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.Expression;
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
		symbEnv.enterScope();
		permEnv.enterScope();
	}

	@Test
	void predConstPassesThroughUnchanged() throws Exception {
		Expression literal = RefinementsParser.createAST("true");
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv);

		Expression result = evaluator.evalPredicate(literal);

		assertSame(literal, result);
	}

	@Test
	void predVarSubstitutesVariableWithSymbolicValue() throws Exception {
		SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x"));

		assertEquals(x.toString(), ExpressionPrettyPrinter.print(result));
	}

	@Test
	void predFieldUsesExistingTrackedFieldValue() throws Exception {
		SymbolicValue x = addVariable("x", Uniqueness.BORROWED);
		SymbolicValue field = symbEnv.addField(x, "isConnected");
		permEnv.add(field, new UniquenessAnnotation(Uniqueness.IMMUTABLE));
		Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected"));

		assertEquals(field.toString(), ExpressionPrettyPrinter.print(result));
	}

	@Test
	void predFieldCreatesFreshFieldForExclusiveReceiver() throws Exception {
		SymbolicValue x = addVariable("x", Uniqueness.FREE);
		Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv);

		Expression result = evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected"));

		SymbolicValue field = symbEnv.get(x, "isConnected");
		assertEquals(field.toString(), ExpressionPrettyPrinter.print(result));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(field));
	}

	@Test
	void predComplexContractPreconditionUsesFixtureMetadata() {
		SymbolicValue thisValue = addVariable("this", Uniqueness.BORROWED);
		SymbolicValue sink = addVariable("sink", Uniqueness.BORROWED);
		Evaluator evaluator = new Evaluator(
			maps,
			Map.of("this", writerType, "sink", readerType),
			symbEnv,
			permEnv);

		Expression result = evaluator.evalPredicate(connectPrecondition);

		SymbolicValue thisConnected = symbEnv.get(thisValue, "isConnected");
		SymbolicValue thisClosed = symbEnv.get(thisValue, "isClosed");
		SymbolicValue sinkConnected = symbEnv.get(sink, "isConnected");
		SymbolicValue sinkClosed = symbEnv.get(sink, "isClosed");
		assertEquals(
			thisConnected + " == false && " + thisClosed + " == false && "
				+ sinkConnected + " == false && " + sinkClosed + " == false",
			ExpressionPrettyPrinter.print(result));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(thisConnected));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(thisClosed));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(sinkConnected));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), permEnv.get(sinkClosed));
	}

	@Test
	void predVarRejectsUnknownVariable() {
		Evaluator missingEvaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv);

		assertThrows(IllegalStateException.class, () -> missingEvaluator.evalPredicate(RefinementsParser.createAST("missing")));
	}

	@Test
	void predVarRejectsSharedVariable() {
		addVariable("x", Uniqueness.SHARED);
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv);

		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(RefinementsParser.createAST("x")));
	}

	@Test
	void predVarRejectsBottomVariable() {
		addVariable("x", Uniqueness.BOTTOM);
		Evaluator evaluator = new Evaluator(maps, Map.of(), symbEnv, permEnv);

		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(RefinementsParser.createAST("x")));
	}

	@Test
	void predFieldRejectsSharedReceiverForNonSharedField() throws Exception {
		addVariable("x", Uniqueness.SHARED);
		Evaluator evaluator = new Evaluator(maps, Map.of("x", writerType), symbEnv, permEnv);

		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(RefinementsParser.createAST("x.isConnected")));
	}

	private SymbolicValue addVariable(String name, Uniqueness permission) {
		SymbolicValue value = symbEnv.addVariable(name);
		permEnv.add(value, new UniquenessAnnotation(permission));
		return value;
	}
}
