package typechecking;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.FieldAccess;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.Var;
import rj_language.visitors.ExpressionPrettyPrinter;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class EvaluatorTest {
	private static ClassLevelMaps maps;
	private static CtTypeReference<?> writerType;

	@BeforeAll
	static void loadFieldMetadata() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new File("./src/test/examples/refinements/PipedWriterCorrect.java").getAbsolutePath());
		CtModel model = launcher.buildModel();

		maps = new ClassLevelMaps();
		model.getRootPackage().accept(new LatteClassFirstPass(
			new SymbolicEnvironment(),
			new PermissionEnvironment(),
			maps));

		CtClass<?> writerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
			.filter(c -> c.getSimpleName().equals("PipedWriter"))
			.findFirst()
			.orElseThrow();
		writerType = writerClass.getReference();
	}

	@Test
	void predConstPassesThroughUnchanged() {
		Evaluator evaluator = new Evaluator(maps);
		LiteralBoolean literal = new LiteralBoolean(true);
		Env env = env();

		Evaluator.PredicateEvalResult result = evaluator.evalPredicate(
			literal,
			Map.of(),
			env.symbEnv,
			env.permEnv,
			new RefinementPath());

		assertSame(literal, result.predicate());
	}

	@Test
	void predVarSubstitutesVariableWithSymbolicValue() {
		Evaluator evaluator = new Evaluator(maps);
		Env env = env();
		SymbolicValue x = addVariable(env, "x", Uniqueness.BORROWED);

		Evaluator.PredicateEvalResult result = evaluator.evalPredicate(
			new Var("x"),
			Map.of(),
			env.symbEnv,
			env.permEnv,
			new RefinementPath());

		assertEquals(x.toString(), ExpressionPrettyPrinter.print(result.predicate()));
	}

	@Test
	void predBinaryThreadsLeftThenRight() {
		Evaluator evaluator = new Evaluator(maps);
		Env env = env();
		SymbolicValue x = addVariable(env, "x", Uniqueness.IMMUTABLE);
		BinaryExpression predicate = new BinaryExpression(new Var("x"), BinaryOperator.EQ, new LiteralBoolean(true));

		Evaluator.PredicateEvalResult result = evaluator.evalPredicate(
			predicate,
			Map.of(),
			env.symbEnv,
			env.permEnv,
			new RefinementPath());

		assertEquals(x + " == true", ExpressionPrettyPrinter.print(result.predicate()));
	}

	@Test
	void predFieldUsesExistingTrackedFieldValue() {
		Evaluator evaluator = new Evaluator(maps);
		Env env = env();
		SymbolicValue x = addVariable(env, "x", Uniqueness.BORROWED);
		SymbolicValue field = env.symbEnv.addField(x, "isConnected");
		env.permEnv.add(field, new UniquenessAnnotation(Uniqueness.IMMUTABLE));

		Evaluator.PredicateEvalResult result = evaluator.evalPredicate(
			new FieldAccess(new Var("x"), "isConnected"),
			Map.of("x", writerType),
			env.symbEnv,
			env.permEnv,
			new RefinementPath());

		assertEquals(field.toString(), ExpressionPrettyPrinter.print(result.predicate()));
	}

	@Test
	void predFieldCreatesFreshFieldForExclusiveReceiver() {
		Evaluator evaluator = new Evaluator(maps);
		Env env = env();
		SymbolicValue x = addVariable(env, "x", Uniqueness.FREE);

		Evaluator.PredicateEvalResult result = evaluator.evalPredicate(
			new FieldAccess(new Var("x"), "isConnected"),
			Map.of("x", writerType),
			env.symbEnv,
			env.permEnv,
			new RefinementPath());

		SymbolicValue field = env.symbEnv.get(x, "isConnected");
		assertEquals(field.toString(), ExpressionPrettyPrinter.print(result.predicate()));
		assertEquals(new UniquenessAnnotation(Uniqueness.IMMUTABLE), env.permEnv.get(field));
	}

	@Test
	void predVarRejectsUnknownSharedAndBottomVariables() {
		Evaluator evaluator = new Evaluator(maps);

		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(
			new Var("missing"),
			Map.of(),
			env().symbEnv,
			env().permEnv,
			new RefinementPath()));

		Env shared = env();
		addVariable(shared, "x", Uniqueness.SHARED);
		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(
			new Var("x"),
			Map.of(),
			shared.symbEnv,
			shared.permEnv,
			new RefinementPath()));

		Env bottom = env();
		addVariable(bottom, "x", Uniqueness.BOTTOM);
		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(
			new Var("x"),
			Map.of(),
			bottom.symbEnv,
			bottom.permEnv,
			new RefinementPath()));
	}

	@Test
	void predFieldRejectsSharedReceiverForNonSharedField() {
		Evaluator evaluator = new Evaluator(maps);
		Env env = env();
		addVariable(env, "x", Uniqueness.SHARED);

		assertThrows(IllegalStateException.class, () -> evaluator.evalPredicate(
			new FieldAccess(new Var("x"), "isConnected"),
			Map.of("x", writerType),
			env.symbEnv,
			env.permEnv,
			new RefinementPath()));
	}

	private static Env env() {
		SymbolicEnvironment symbEnv = new SymbolicEnvironment();
		PermissionEnvironment permEnv = new PermissionEnvironment();
		symbEnv.enterScope();
		permEnv.enterScope();
		return new Env(symbEnv, permEnv);
	}

	private static SymbolicValue addVariable(Env env, String name, Uniqueness permission) {
		SymbolicValue value = env.symbEnv.addVariable(name);
		env.permEnv.add(value, new UniquenessAnnotation(permission));
		return value;
	}

	private record Env(SymbolicEnvironment symbEnv, PermissionEnvironment permEnv) {
	}
}
