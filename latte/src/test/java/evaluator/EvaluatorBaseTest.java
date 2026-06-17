package evaluator;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementContract;
import context.SymbolicEnvironment;
import context.SymbolicValue;
import context.TypeEnvironment;
import context.Uniqueness;
import context.UniquenessAnnotation;
import rj_language.ast.Expression;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import typechecking.LatteClassFirstPass;
import typechecking.RefinementFirstPass;

public class EvaluatorBaseTest extends EvaluatorTestSupport {
	protected static ClassLevelMaps maps;
	protected static CtTypeReference<?> writerType;
	protected static CtTypeReference<?> readerType;
	protected static Expression connectPrecondition;

	@BeforeAll
	static void loadFieldMetadata() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new File("./src/test/examples/refinements/PipedOutputStreamCorrect.java").getAbsolutePath());
		CtModel model = launcher.buildModel();

		maps = new ClassLevelMaps();
		model.getRootPackage().accept(
			new LatteClassFirstPass(
			new TypeEnvironment(),
			new SymbolicEnvironment(),
			new PermissionEnvironment(),
			maps));
		model.getRootPackage().accept(new RefinementFirstPass(
			new TypeEnvironment(),
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

	protected SymbolicValue addVariable(String name, Uniqueness permission) {
		SymbolicValue value = symbEnv.addVariable(name);
		permEnv.add(value, new UniquenessAnnotation(permission));
		return value;
	}
}
