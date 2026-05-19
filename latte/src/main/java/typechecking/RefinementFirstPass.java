package typechecking;

import java.lang.annotation.Annotation;

import context.ClassLevelMaps;
import context.MethodRefinementContract;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
import rj_language.ast.Expression;
import rj_language.parsing.ParsingException;
import rj_language.parsing.RefinementsParser;
import rj_language.visitors.ExpressionPrettyPrinter;
import specification.lj.Refinement;
import specification.lj.StateRefinement;
import specification.lj.StateRefinementMultiple;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import utils.Constants;

public class RefinementFirstPass extends LatteAbstractChecker {
    public RefinementFirstPass(SymbolicEnvironment se, PermissionEnvironment pe, ClassLevelMaps mtc) {
        super(se, pe, mtc);
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
			CtClass<?> klass = (CtClass<?>) parent;
			String className = klass.getSimpleName();
			for (CtAnnotation<? extends Annotation> ann : f.getAnnotations()) {
				Annotation actual = ann.getActualAnnotation();
				if (actual != null && actual.annotationType().getSimpleName().equals("Ghost")) {
					f.putMetadata(utils.Constants.FIELD_GHOST_KEY, Boolean.TRUE);
					logInfo(String.format("Field %s marked as @Ghost in class %s", fieldName, className));
					break;
				}
			}
		    Expression refinement = extractRefinement(f);
		    if (refinement != null) {
		        f.putMetadata(Constants.FIELD_REFINEMENT_KEY, refinement);
		        logInfo(String.format("Field %s has refinement %s", fieldName, ExpressionPrettyPrinter.print(refinement)));
		    } else {
		        logInfo(String.format("Field %s has no refinement annotation", fieldName));
		    }
			maps.addFieldClass(f, klass);
			logInfo(String.format("Added field %s to class %s in the mappings", fieldName, className));
		} else {
			logWarning(String.format("Field %s has no class parent while extracting refinements", fieldName));
		}
        super.visitCtField(f);
		loggingSpaces--;
    }

    @Override
    public <T> void visitCtMethod(CtMethod<T> m) {
		String methodName = m.getSimpleName();
		int params = m.getParameters().size();
		logInfo("Visiting method: " + methodName, m);
		loggingSpaces++;
		CtElement parent = m.getParent();
		if (parent instanceof CtClass) {
			CtClass<?> klass = (CtClass<?>) parent;
			MethodRefinementContract contract = extractContract(m);
			m.putMetadata(Constants.METHOD_CONTRACT_KEY, contract);
			maps.addMethod(klass, m);
			logInfo(String.format("Added method %s/%d to class %s mappings",
				methodName, params, klass.getSimpleName()));
			logInfo(String.format("Parsed method %s refinements: method=%s, params=%d, transitions=%d",
				methodName,
				ExpressionPrettyPrinter.print(contract.getMethodRefinement()),
				contract.getParameterRefinements().size(),
				contract.getStateTransitions().size()));
			logInfo(String.format("Stored contract for method %s: pre=%s, post=%s",
				methodName,
				contract.getCombinedPrecondition(),
				contract.getCombinedPostcondition()));
		} else {
			logWarning(String.format("Method %s has no class parent while extracting refinements", methodName));
		}
        super.visitCtMethod(m);
		loggingSpaces--;
    }

    @Override
    public <T> void visitCtConstructor(CtConstructor<T> c) {
		String constructorName = c.getSimpleName();
		int params = c.getParameters().size();
		logInfo("Visiting constructor: " + constructorName, c);
		loggingSpaces++;
		CtElement parent = c.getParent();
		if (parent instanceof CtClass) {
			CtClass<?> klass = (CtClass<?>) parent;
			MethodRefinementContract contract = extractContract(c);
			c.putMetadata(Constants.CONSTRUCTOR_CONTRACT_KEY, contract);
			maps.addConstructor(klass, c);
			logInfo(String.format("Added constructor %s/%d to class %s mappings",
				constructorName, params, klass.getSimpleName()));
			logInfo(String.format("Parsed constructor %s refinements: params=%d, transitions=%d",
				constructorName,
				contract.getParameterRefinements().size(),
				contract.getStateTransitions().size()));
			logInfo(String.format("Stored contract for constructor %s: pre=%s, post=%s",
				constructorName,
				contract.getCombinedPrecondition(),
				contract.getCombinedPostcondition()));
		} else {
			logWarning(String.format("Constructor %s has no class parent while extracting refinements", constructorName));
		}
        super.visitCtConstructor(c);
		loggingSpaces--;
    }

	private Expression extractRefinement(CtElement element) {
		for (CtAnnotation<? extends Annotation> ann : element.getAnnotations()) {
			Annotation actual = ann.getActualAnnotation();
			if (actual instanceof Refinement refinement) {
				String value = normalize(refinement.value());
				logInfo(String.format("Parsed @Refinement on %s: value=%s", describeElement(element), value));
				return parsePredicate(value, element, "@Refinement");
			}
		}
		logInfo(String.format("No @Refinement found on %s", describeElement(element)));
		return null;
	}

	private MethodRefinementContract extractContract(CtMethod<?> method) {
		MethodRefinementContract contract = new MethodRefinementContract();
		contract.setMethodRefinement(extractRefinement(method));
		extractParameterRefinements(contract, method.getParameters());
		extractStateRefinements(contract, method);
		return contract;
	}

	private MethodRefinementContract extractContract(CtConstructor<?> constructor) {
		MethodRefinementContract contract = new MethodRefinementContract();
		extractParameterRefinements(contract, constructor.getParameters());
		extractStateRefinements(contract, constructor);
		return contract;
	}

	private void extractParameterRefinements(MethodRefinementContract contract, Iterable<? extends CtParameter<?>> parameters) {
		for (CtParameter<?> p : parameters) {
			Expression refinement = extractRefinement(p);
			if (refinement != null) {
				contract.addParameterRefinement(p.getSimpleName(), refinement);
				logInfo(String.format("Parameter %s has refinement %s",
					p.getSimpleName(), ExpressionPrettyPrinter.print(refinement)));
			} else {
				logInfo(String.format("Parameter %s has no refinement annotation", p.getSimpleName()));
			}
		}
	}

	private void extractStateRefinements(MethodRefinementContract contract, CtElement executable) {
		boolean hasStateRefinement = false;
		for (CtAnnotation<? extends Annotation> ann : executable.getAnnotations()) {
			Annotation actual = ann.getActualAnnotation();
			if (actual instanceof StateRefinement stateRefinement) {
				hasStateRefinement = true;
				addStateTransition(contract, stateRefinement);
			} else if (actual instanceof StateRefinementMultiple stateRefinementMultiple) {
				hasStateRefinement = true;
				logInfo(String.format("Found @StateRefinementMultiple on %s with %d transitions",
					describeElement(executable), stateRefinementMultiple.value().length));
				for (StateRefinement stateRefinement : stateRefinementMultiple.value()) {
					addStateTransition(contract, stateRefinement);
				}
			}
		}
		if (!hasStateRefinement) {
			logInfo(String.format("No state refinements found on %s", describeElement(executable)));
		}
	}

	private void addStateTransition(MethodRefinementContract contract, StateRefinement stateRefinement) {
		String from = normalize(stateRefinement.from());
		String to = normalize(stateRefinement.to());
		String msg = normalize(stateRefinement.msg());
		logInfo(String.format("Parsed state transition: from=%s, to=%s, msg=%s", from, to, msg));
		Expression fromExpr = parsePredicate(from, null, "@StateRefinement.from");
		Expression toExpr = parsePredicate(to, null, "@StateRefinement.to");
		contract.addStateTransition(
			fromExpr,
			toExpr,
			msg);
	}

	private Expression parsePredicate(String predicate, CtElement element, String label) {
		String normalized = normalize(predicate);
		if (normalized == null) {
			return null;
		}
		try {
			return RefinementsParser.createAST(normalized);
		} catch (ParsingException e) {
			String location = element == null ? label : label + " on " + describeElement(element);
			logWarning(String.format("Failed to parse %s: %s", location, normalized));
			return null;
		}
	}

	private String describeElement(CtElement element) {
		if (element == null) {
			return "unknown element";
		}
		if (element instanceof CtMethod) {
			CtMethod<?> ctMethod = (CtMethod<?>) element;
			return "method " + ctMethod.getSimpleName();
		}
		if (element instanceof CtConstructor) {
			CtConstructor<?> ctConstructor = (CtConstructor<?>) element;
			return "constructor " + ctConstructor.getSimpleName();
		}
		if (element instanceof CtField) {
			CtField<?> ctField = (CtField<?>) element;
			return "field " + ctField.getSimpleName();
		}
		if (element instanceof CtParameter) {
			CtParameter<?> ctParameter = (CtParameter<?>) element;
			return "parameter " + ctParameter.getSimpleName();
		}
		if (element instanceof CtClass) {
			CtClass<?> ctClass = (CtClass<?>) element;
			return "class " + ctClass.getSimpleName();
		}
		return element.getClass().getSimpleName();
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}