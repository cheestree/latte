package typechecking;

import java.lang.annotation.Annotation;

import context.ClassLevelMaps;
import context.MethodRefinementContract;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
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
import spoon.reflect.reference.CtTypeReference;

public class RefinementFirstPass extends LatteAbstractChecker {
    public RefinementFirstPass(SymbolicEnvironment se, PermissionEnvironment pe, ClassLevelMaps mtc) {
        super(se, pe, mtc);
        logInfo("[ Refinement Pass started ]");
        enterScopes();
    }

	@Override
	public <T> void visitCtClass(CtClass<T> ctClass) {
		logInfo("Visiting class: " + ctClass.getSimpleName(), ctClass);
		CtTypeReference<?> typeRef = ctClass.getReference();
		maps.addTypeClass(typeRef, ctClass);
		logInfo(String.format("Registered class %s in type mappings", ctClass.getSimpleName()));
		super.visitCtClass(ctClass);
	}

    @Override
    public <T> void visitCtField(CtField<T> f) {
		logInfo("Visiting field: " + f.getSimpleName(), f);
		loggingSpaces++;
		CtElement parent = f.getParent();
		if (parent instanceof CtClass) {
			CtClass<?> klass = (CtClass<?>) parent;
			maps.addFieldClass(f, klass);
			logInfo(String.format("Added field %s to class %s in the mappings", f.getSimpleName(), klass.getSimpleName()));
		} else {
			logWarning(String.format("Field %s has no class parent while extracting refinements", f.getSimpleName()));
		}
        String refinement = extractRefinement(f);
        if (refinement != null) {
            maps.addFieldRefinement(f, refinement);
            logInfo(String.format("Field %s has refinement %s", f.getSimpleName(), refinement));
		} else {
			logInfo(String.format("Field %s has no refinement annotation", f.getSimpleName()));
        }
        super.visitCtField(f);
		loggingSpaces--;
    }

    @Override
    public <T> void visitCtMethod(CtMethod<T> m) {
		logInfo("Visiting method: " + m.getSimpleName(), m);
		loggingSpaces++;
		CtElement parent = m.getParent();
		if (parent instanceof CtClass) {
			CtClass<?> klass = (CtClass<?>) parent;
			maps.addMethod(klass, m);
			logInfo(String.format("Added method %s/%d to class %s mappings",
				m.getSimpleName(), m.getParameters().size(), klass.getSimpleName()));
		} else {
			logWarning(String.format("Method %s has no class parent while extracting refinements", m.getSimpleName()));
		}
		MethodRefinementContract contract = extractContract(m);
		maps.addMethodContract(m, contract);
		logInfo(String.format("Parsed method %s refinements: method=%s, params=%d, transitions=%d",
			m.getSimpleName(),
			contract.getMethodRefinement(),
			contract.getParameterRefinements().size(),
			contract.getStateTransitions().size()));
		logInfo(String.format("Stored contract for method %s: pre=%s, post=%s", m.getSimpleName(),
			contract.getCombinedPrecondition(), contract.getCombinedPostcondition()));
        super.visitCtMethod(m);
		loggingSpaces--;
    }

    @Override
    public <T> void visitCtConstructor(CtConstructor<T> c) {
		logInfo("Visiting constructor: " + c.getSimpleName(), c);
		loggingSpaces++;
		CtElement parent = c.getParent();
		if (parent instanceof CtClass) {
			CtClass<?> klass = (CtClass<?>) parent;
			maps.addConstructor(klass, c);
			logInfo(String.format("Added constructor %s/%d to class %s mappings",
				c.getSimpleName(), c.getParameters().size(), klass.getSimpleName()));
		} else {
			logWarning(String.format("Constructor %s has no class parent while extracting refinements", c.getSimpleName()));
		}
		MethodRefinementContract contract = extractContract(c);
		maps.addConstructorContract(c, contract);
		logInfo(String.format("Parsed constructor %s refinements: params=%d, transitions=%d",
			c.getSimpleName(),
			contract.getParameterRefinements().size(),
			contract.getStateTransitions().size()));
		logInfo(String.format("Stored contract for constructor %s: pre=%s, post=%s", c.getSimpleName(),
			contract.getCombinedPrecondition(), contract.getCombinedPostcondition()));
        super.visitCtConstructor(c);
		loggingSpaces--;
    }

    private String extractRefinement(CtElement element) {
		for (CtAnnotation<? extends Annotation> ann : element.getAnnotations()) {
			Annotation actual = ann.getActualAnnotation();
			if (actual instanceof Refinement refinement) {
				String raw = refinement.value();
				String predicate = normalize(refinement.value());
				logInfo(String.format("Parsed @Refinement on %s: raw=%s, normalized=%s",
					describeElement(element), raw, predicate));
				if (predicate != null) {
					return predicate;
				}
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
			String predicate = extractRefinement(p);
			if (predicate != null) {
				contract.addParameterRefinement(p.getSimpleName(), predicate);
				logInfo(String.format("Parameter %s has refinement %s", p.getSimpleName(), predicate));
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
		contract.addStateTransition(
			from,
			to,
			msg);
	}

	private String describeElement(CtElement element) {
		if (element instanceof CtMethod<?> ctMethod) {
			return "method " + ctMethod.getSimpleName();
		}
		if (element instanceof CtConstructor<?> ctConstructor) {
			return "constructor " + ctConstructor.getSimpleName();
		}
		if (element instanceof CtField<?> ctField) {
			return "field " + ctField.getSimpleName();
		}
		if (element instanceof CtParameter<?> ctParameter) {
			return "parameter " + ctParameter.getSimpleName();
		}
		if (element instanceof CtClass<?> ctClass) {
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