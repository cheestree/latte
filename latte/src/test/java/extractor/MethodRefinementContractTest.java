package extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import context.MethodRefinementContract;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import typechecking.RefinementFirstPass;
import helpers.TestModelHelper;

public class MethodRefinementContractTest {
    @Test
    public void testMethodContractsParsed() {
        String source = "./src/test/examples/refinements/PipedWriterCorrect.java";
        var model = TestModelHelper.loadModel(source);

        ClassLevelMaps maps = new ClassLevelMaps();
        RefinementFirstPass pass = new RefinementFirstPass(new SymbolicEnvironment(), new PermissionEnvironment(), maps);
        model.getRootPackage().accept(pass);

        CtClass<?> writerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
            .filter(c -> c.getSimpleName().equals("PipedWriter"))
            .findFirst()
            .orElseThrow();

        MethodRefinementContract writerConnect = maps.getMethodContract(writerClass, "connect", 1);
        assertNotNull(writerConnect);
        assertEquals("this.isConnected == false && reader.isConnected == false", writerConnect.getCombinedPrecondition());
        assertEquals("this.isConnected == true && reader.isConnected == true", writerConnect.getCombinedPostcondition());
    }
}
