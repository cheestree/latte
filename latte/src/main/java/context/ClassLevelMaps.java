package context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import utils.Constants;
import rj_language.ast.Expression;

public class ClassLevelMaps {

    static ClassLevelMaps instance;
    Map<CtTypeReference<?>, CtClass<?>> typeClassMap;
    Map<CtClass<?>, Map<String, CtField<?>>> classFields;
    Map<CtClass<?>, Map<Integer, CtConstructor<?>>> classConstructors;
    Map<CtClass<?>, Map<Pair<String, Integer>, CtMethod<?>>> classMethods;


    public ClassLevelMaps() {
        typeClassMap = new HashMap<CtTypeReference<?>, CtClass<?>>();
        classFields = new HashMap<CtClass<?>, Map<String, CtField<?>>>();
        classConstructors = new HashMap<>();
        classMethods = new HashMap<>();
    }

    public CtClass<?> getClassFrom(CtTypeReference<?> type) {
        return typeClassMap.get(type);
    }

    public void addTypeClass(CtTypeReference<?> type, CtClass<?> klass) {
        typeClassMap.put(type, klass);
    } 

    public void addConstructor(CtClass<?> klass, CtConstructor<?> constructor) {
        int params = constructor.getParameters().size();
        if (classConstructors.containsKey(klass)){
            Map<Integer, CtConstructor<?>> m = classConstructors.get(klass);
            m.put(params, constructor);
        } else {
            Map<Integer, CtConstructor<?>> m = new HashMap<Integer, CtConstructor<?>>();
            m.put(params, constructor);
            classConstructors.put(klass, m);
        }
    }

    public void addMethod(CtClass<?> klass, CtMethod<?> method) {
        Pair<String, Integer> mPair = Pair.of(method.getSimpleName(), method.getParameters().size());
        if (classMethods.containsKey(klass)){
            Map<Pair<String, Integer>, CtMethod<?>> m = classMethods.get(klass);
            m.put(mPair, method);
        } else {
            Map<Pair<String, Integer>, CtMethod<?>> m = new HashMap<Pair<String, Integer>, CtMethod<?>>();
            m.put(mPair, method);
            classMethods.put(klass, m);
        }
    }

    public void addFieldClass(CtField<?> field, CtClass<?> klass) {
        if (classFields.containsKey(klass)){
            Map<String, CtField<?>> m = classFields.get(klass);
            m.put(field.getSimpleName(), field);
        } else {
            Map<String, CtField<?>> m = new HashMap<String, CtField<?>>();
            m.put(field.getSimpleName(), field);
            classFields.put(klass, m);
        }
    }

    public boolean isGhostField(String className, String fieldName) {
        for (Map.Entry<CtClass<?>, Map<String, CtField<?>>> e : classFields.entrySet()) {
            CtClass<?> klass = e.getKey();
            if (klass.getSimpleName().equals(className)) {
                Map<String, CtField<?>> fields = e.getValue();
                if (fields != null && fields.containsKey(fieldName)) {
                    return Boolean.TRUE.equals(fields.get(fieldName).getMetadata(Constants.FIELD_GHOST_KEY));
                }
            }
        }
        return false;
    }

    public UniquenessAnnotation getFieldAnnotation(String fieldName, CtTypeReference<?> type) {
        CtClass<?> klass = getClassFrom(type);
        if (classFields.containsKey(klass)){
            Map<String, CtField<?>> m = classFields.get(klass);
            if (m.containsKey(fieldName)){
                CtField<?> field = m.get(fieldName);
                UniquenessAnnotation annotation = new UniquenessAnnotation(field);
                return annotation;
            }
        }
        return null;
    }

    public Expression getFieldRefinement(String fieldName, CtTypeReference<?> type) {
        CtClass<?> klass = getClassFrom(type);
        if (classFields.containsKey(klass)) {
            Map<String, CtField<?>> m = classFields.get(klass);
            if (m.containsKey(fieldName)) {
                CtField<?> field = m.get(fieldName);
                return (Expression) field.getMetadata(Constants.FIELD_REFINEMENT_KEY);
            }
        }
        return null;
    }

    public CtConstructor<?> getCtConstructor (CtClass<?> klass, int numParams){
        if (classConstructors.containsKey(klass)){
            Map<Integer, CtConstructor<?>> l = classConstructors.get(klass);
            if (l.containsKey(numParams)){
                return l.get(numParams);
            }
        }
        return null;
    }

    public RefinementContract getConstructorContract(CtClass<?> klass, int numParams) {
        CtConstructor<?> c = getCtConstructor(klass, numParams);
        if (c == null) {
            return null;
        }
        return (RefinementContract) c.getMetadata(Constants.CONSTRUCTOR_CONTRACT_KEY);
    }

    public CtMethod<?> getCtMethod(CtClass<?> klass, String methodName, int numParams){
        Pair<String, Integer> mPair = Pair.of(methodName, numParams);
        if (classMethods.containsKey(klass)){
            Map<Pair<String, Integer>, CtMethod<?>> m = classMethods.get(klass);
            if (m.containsKey(mPair)){
                return m.get(mPair);
            }
        }
        return null;
    } 

    public RefinementContract getMethodContract(CtClass<?> klass, String methodName, int numParams) {
        CtMethod<?> m = getCtMethod(klass, methodName, numParams);
        if (m == null) {
            return null;
        }
        return (RefinementContract) m.getMetadata(Constants.METHOD_CONTRACT_KEY);
    }

    public static void simplify(SymbolicEnvironment symbEnv, PermissionEnvironment permEnv) {
        // 1) Remove unreachable values
        List<SymbolicValue> removed = symbEnv.removeUnreachableValues();

        // 2) Remove the values from permEnv
        permEnv.removeValues(removed);

        // Unique Values
        // 1) get all the symbolic values v that are unique in permEnv
        List<SymbolicValue> lsv = permEnv.getUniqueValues();

        // 2) for each of lsv, check if any of the values in symbEnv are equal to v
       for (SymbolicValue v : lsv) {
            // SimpUnique - no field with v as the symbolic value
            if (!symbEnv.hasFieldWithValue(v)){
                // 3) if no, v can be free in permEnv
                permEnv.add(v, new UniquenessAnnotation(Uniqueness.FREE));
            }
       }
    }


	public static void joinDropVar(SymbolicEnvironment symbEnv, SymbolicEnvironment  symb) {
		List<VariableHeapLoc> remove = new ArrayList<>(); 
		for (VariableHeapLoc v : symb.keySet()) 
			if (v instanceof Variable && !symbEnv.contains(v))
				remove.add(v);
		for (VariableHeapLoc v : remove)
			symb.remove(v);
	}


    public static void joinDropField(SymbolicEnvironment symbEnv) {
        List<VariableHeapLoc> remove = new ArrayList<>(); 
        for (VariableHeapLoc v : symbEnv.keySet()) 
            if (v instanceof FieldHeapLoc)
                remove.add(v);
        for (VariableHeapLoc v : remove)
            symbEnv.remove(v);
    }
			
	public static void joinUnify( 
        SymbolicEnvironment symbEnv,     PermissionEnvironment permEnv,
        SymbolicEnvironment thenSymbEnv, PermissionEnvironment thenPermEnv, 
		SymbolicEnvironment elseSymbEnv, PermissionEnvironment elsePermEnv) {
		
        // JoinUnifyVar
		for(VariableHeapLoc v: thenSymbEnv.keySet()){
			if(v instanceof Variable){
                Variable x = (Variable) v;
                if( elseSymbEnv.contains(x)){
                    SymbolicValue v1 = thenSymbEnv.get(x), 
                        v2 = elseSymbEnv.get(x);

                    // Σ1 (𝜈1) ∧ Σ2 (𝜈2) ⇛ 𝛼
                    UniquenessAnnotation freshUA = 
                        UniquenessAnnotation.unifyAnnotation(thenPermEnv.get(v1), elsePermEnv.get(v2));

                    // fresh 𝜈
                    SymbolicValue freshV = symbEnv.getFresh();
                    // Δ; Σ ⊢ Δ1 [𝜈/𝜈1]; 𝜈: 𝛼, Σ1 ∧ Δ2 [𝜈/𝜈2]; 𝜈: 𝛼, Σ2 ⇛ Δ′; Σ′
                    thenSymbEnv.updateAll(v1, freshV);
                    elseSymbEnv.updateAll(v2, freshV);

                    thenPermEnv.add(freshV, freshUA);
                    elsePermEnv.add(freshV, freshUA);
                }
            } else {
                // JoinUnifyField
                FieldHeapLoc x = (FieldHeapLoc) v;
                if( elseSymbEnv.contains(x)){
                    SymbolicValue v1 = thenSymbEnv.get(x), v2 = elseSymbEnv.get(x);
                    // Σ1 (𝜈1) ∧ Σ2 (𝜈2) ⇛ 𝛼
                    UniquenessAnnotation freshUA = 
                        UniquenessAnnotation.unifyAnnotation(thenPermEnv.get(v1), elsePermEnv.get(v2));

                    // fresh 𝜈
                    SymbolicValue freshV = symbEnv.getFresh();
                    // Δ; Σ ⊢ Δ1 [𝜈/𝜈1]; 𝜈: 𝛼, Σ1 ∧ Δ2 [𝜈/𝜈2]; 𝜈: 𝛼, Σ2 ⇛ Δ′; Σ′
                    thenSymbEnv.updateAll(v1, freshV);
                    elseSymbEnv.updateAll(v2, freshV);

                    thenPermEnv.add(freshV, freshUA);
                    elsePermEnv.add(freshV, freshUA);
                }
            }
		}
	}

    public static void joinElim( 
        SymbolicEnvironment symbEnv,     PermissionEnvironment permEnv,
        SymbolicEnvironment thenSymbEnv, PermissionEnvironment thenPermEnv, 
		SymbolicEnvironment elseSymbEnv, PermissionEnvironment elsePermEnv) {
                
        for ( VariableHeapLoc v: thenSymbEnv.keySet()){
            if( v instanceof Variable){
                // joinElimVar
                Variable x = (Variable) v;
                if(elseSymbEnv.contains(x) && symbEnv.contains(x)){
                    SymbolicValue v1 = thenSymbEnv.get(x), v2 = elseSymbEnv.get(x), vSymb = symbEnv.get(x);
                    UniquenessAnnotation ua1 = thenPermEnv.get(v1), ua2 = elsePermEnv.get(v2);
                    if (v1.equals(v2) && ua1.equals(ua2)){
                        symbEnv.updateAll(vSymb, v1);
                        permEnv.add(v1, ua1);
                    }
                }
            } else {
                // joinElimField
                FieldHeapLoc x = (FieldHeapLoc) v;
                if(elseSymbEnv.contains(x)){
                    SymbolicValue v1 = thenSymbEnv.get(x), v2 = elseSymbEnv.get(x);
                    UniquenessAnnotation ua1 = thenPermEnv.get(v1), ua2 = elsePermEnv.get(v2);
                    if (v1.equals(v2) && ua1.equals(ua2)){
                        symbEnv.add(x, v1);
                        permEnv.add(v1, ua1);
                    }
                } else {
                    // joinDropFieldInner
                    SymbolicValue v1 = thenSymbEnv.get(x);
                    permEnv.add(v1, new UniquenessAnnotation(Uniqueness.BOTTOM));                    
                }
            }
        }

        for ( VariableHeapLoc v: elseSymbEnv.keySet()){
            if(v instanceof FieldHeapLoc)
                if (!thenSymbEnv.contains(v)){
                    // joinDropFieldOuter
                    SymbolicValue v1 = elseSymbEnv.get(v);
                    permEnv.add(v1, new UniquenessAnnotation(Uniqueness.BOTTOM));
                }
        }
	}



}
