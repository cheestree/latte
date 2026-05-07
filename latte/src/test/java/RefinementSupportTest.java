import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import context.MethodRefinementContract;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
import rj_language.ast.Expression;
import rj_language.ast.LiteralInt;
import rj_language.parsing.ParsingException;
import rj_language.parsing.PredicateSubstitution;
import rj_language.parsing.RefinementsParser;
import rj_language.visitors.ExpressionPrettyPrinter;
import rj_language.visitors.ExpressionSubstitutionVisitor;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import typechecking.RefinementFirstPass;

public class RefinementSupportTest {

    @Test
    public void testFirstPassExtractsRefinementContracts() {
        String source = """
                import specification.lj.Refinement;
                import specification.lj.StateRefinement;
                import specification.Unique;

                class SampleRefinement {
                    @Refinement(\"this.v >= 0\")
                    int v;

                    @StateRefinement(to = \"ready(this)\")
                    SampleRefinement() {}

                    @Refinement(\"result >= x\")
                    @StateRefinement(from = \"ready(this)\", to = \"done(this)\")
                    int inc(@Refinement(\"x >= 0\") int x) {
                        return x + 1;
                    }
                }
                """;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source, "SampleRefinement.java"));
        CtModel model = launcher.buildModel();

        ClassLevelMaps maps = new ClassLevelMaps();
        RefinementFirstPass pass = new RefinementFirstPass(new SymbolicEnvironment(), new PermissionEnvironment(), maps);
        model.getRootPackage().accept(pass);

        CtClass<?> klass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
                .filter(c -> c.getSimpleName().equals("SampleRefinement"))
                .findFirst()
                .orElseThrow();

        MethodRefinementContract methodContract = maps.getMethodContract(klass, "inc", 1);
        assertNotNull(methodContract);
        assertEquals("result >= x", methodContract.getMethodRefinement());
        assertEquals("x >= 0", methodContract.getParameterRefinement("x"));
        assertEquals("(ready(this))", methodContract.getCombinedPrecondition());
        assertEquals("(done(this))", methodContract.getCombinedPostcondition());

        MethodRefinementContract constructorContract = maps.getConstructorContract(klass, 0);
        assertNotNull(constructorContract);
        assertEquals("(ready(this))", constructorContract.getCombinedPostcondition());

        String fieldRefinement = maps.getFieldRefinement("v", klass.getReference());
        assertEquals("this.v >= 0", fieldRefinement);
    }

    @Test
    public void testPredicateSubstitutionOnVariables() throws ParsingException {
        String toSubstitute = "x + 1 < y";
        String substituteWith = "z - 2";
        String substituted = PredicateSubstitution.substituteToString(toSubstitute, "x", substituteWith);
        System.out.println("Substituted predicate " + toSubstitute + " with " + substituteWith + ". Result: " + substituted + " (expected: z - 2 + 1 < y");
        assertEquals("z - 2 + 1 < y", substituted);
    }

    @Test
    public void testPredicateSubstitutionOnOldFieldAccess() throws ParsingException {
        String toSubstitute = "old(x.f) == 0";
        String substituteWith = "this";
        String substituted = PredicateSubstitution.substituteToString(toSubstitute, "x", substituteWith);
        System.out.println("Substituted predicate " + toSubstitute + " with " + substituteWith + ". Result: " + substituted + " (expected: old(this.f) == 0)");
        assertEquals("old(this.f) == 0", substituted);
    }

    @Test
    public void testPredicateSubstitutionRejectsInvalidFieldReceiverSubstitution() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PredicateSubstitution.substitute("x.f == 0", "x", "z + 1"));
        assertNotNull(error);
    }

    // Additional test to ensure that substitution does not mutate the original expression. This is important to verify that the substitution process is functional and does not have side effects on the input expression.
    @Test
    void testSubstitutionDoesNotMutateOriginal() throws ParsingException {
        Expression original = RefinementsParser.createAST("x > 5");
        String before = ExpressionPrettyPrinter.print(original);
        
        ExpressionSubstitutionVisitor.substitute(original, "x", new LiteralInt(3));
        
        String after = ExpressionPrettyPrinter.print(original);
        assertEquals(before, after, "Original expression was mutated by substitution");
    }
}