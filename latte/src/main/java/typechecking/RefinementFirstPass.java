package typechecking;

import java.lang.annotation.Annotation;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.RefinementContract;
import context.SymbolicEnvironment;
import context.TypeEnvironment;
import rj_language.ast.Expression;
import rj_language.parsing.ParsingException;
import rj_language.parsing.RefinementsParser;
import specification.lj.Refinement;
import specification.lj.StateRefinement;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import utils.Constants;

public class RefinementFirstPass extends LatteAbstractChecker {
    public RefinementFirstPass(SymbolicEnvironment se, PermissionEnvironment pe, ClassLevelMaps mtc) {
        this(new TypeEnvironment(), se, pe, mtc);
    }

    public RefinementFirstPass(TypeEnvironment te, SymbolicEnvironment se, PermissionEnvironment pe, ClassLevelMaps mtc) {
        super(te, se, pe, mtc);
        logInfo("[ Refinement Pass started ]");
        enterScopes();
    }

    @Override
    public <T> void visitCtField(CtField<T> f) {
		String fieldName = f.getSimpleName();
		logInfo("Visiting field: " + fieldName, f);
		loggingSpaces++;
		CtElement parent = f.getParent();
		if (parent instanceof CtClass) {
			for (CtAnnotation<? extends Annotation> ann : f.getAnnotations()) {
				Annotation actual = ann.getActualAnnotation();
				if (actual != null && actual.annotationType().getSimpleName().equals("Ghost")) {
					f.putMetadata(utils.Constants.FIELD_GHOST_KEY, Boolean.TRUE);
					break;
				}
			}
			Expression refinement = extractRefinement(f);
			if (refinement != null) {
				f.putMetadata(Constants.FIELD_REFINEMENT_KEY, refinement);
			}
		}
        super.visitCtField(f);
		loggingSpaces--;
    }

    @Override
    public <T> void visitCtMethod(CtMethod<T> m) {
		String methodName = m.getSimpleName();
		logInfo("Visiting method: " + methodName, m);
		loggingSpaces++;
		CtElement parent = m.getParent();
		if (parent instanceof CtClass) {
			RefinementContract contract = extractContract(m);
			m.putMetadata(Constants.METHOD_CONTRACT_KEY, contract);
		}
        super.visitCtMethod(m);
		loggingSpaces--;
    }

    @Override
    public <T> void visitCtConstructor(CtConstructor<T> c) {
		String constructorName = c.getSimpleName();
		logInfo("Visiting constructor: " + constructorName, c);
		loggingSpaces++;
		CtElement parent = c.getParent();
		if (parent instanceof CtClass) {
			RefinementContract contract = extractContract(c);
			c.putMetadata(Constants.CONSTRUCTOR_CONTRACT_KEY, contract);
		}
        super.visitCtConstructor(c);
		loggingSpaces--;
    }

	/***
	 * Extracts the refinement expression from the @Refinement annotation on the given element, if present.
	 * @param element
	 * @return the parsed refinement expression, or null if no @Refinement annotation is found or if parsing fails
	 */
	private Expression extractRefinement(CtElement element) {
		for (CtAnnotation<? extends Annotation> ann : element.getAnnotations()) {
			Annotation actual = ann.getActualAnnotation();
			if (actual instanceof Refinement refinement) {
				return getExpression(refinement.value());
			}
		}
		return null;
	}

	/***
	 * Extracts the state transition contract from the @StateRefinement annotation on the given executable, if present.
	 * Parses the 'from' and 'to' predicates and the custom error message, and returns them as a RefinementContract object. If parsing fails, logs a warning and returns a contract with null predicates and the provided message (if any).
	 * @param executable
	 * @return a RefinementContract containing the parsed 'from' and 'to' predicates and the custom error message, or null if no @StateRefinement annotation is found
	 */
	private RefinementContract extractContract(CtExecutable<?> executable) {
		Expression from = null, to = null;
		String msg = null;
		for (CtAnnotation<? extends Annotation> ann : executable.getAnnotations()) {
			if (ann.getActualAnnotation() instanceof StateRefinement stateRefinement) {
				from = getExpression(stateRefinement.from());
				to = getExpression(stateRefinement.to());
				msg = stateRefinement.msg();
			}
		}
		return new RefinementContract(from, to, msg);	
	}

	private Expression getExpression(String predicate) {
		if (predicate == null || predicate.isBlank()) return null;
		try {
			return RefinementsParser.createAST(predicate);
		} catch (ParsingException e) {
			logWarning("Failed to parse predicate: " + predicate + ". Error: " + e.getMessage());
			return null;
		}
	}
}
