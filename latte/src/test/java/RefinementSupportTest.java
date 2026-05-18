import java.io.File;
import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import context.MethodRefinementContract;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import typechecking.RefinementFirstPass;

public class RefinementSupportTest {
    @Test
    public void testFirstPassExtractsRefinementContracts() {
        String source = "./src/test/examples/refinements/PipedWriterCorrect.java";
        File file = new File(source);
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(file.getAbsolutePath());
        CtModel model = launcher.buildModel();

        ClassLevelMaps maps = new ClassLevelMaps();
        RefinementFirstPass pass = new RefinementFirstPass(new SymbolicEnvironment(), new PermissionEnvironment(), maps);
        model.getRootPackage().accept(pass);

        CtClass<?> writerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
            .filter(c -> c.getSimpleName().equals("PipedWriter"))
            .findFirst()
            .orElseThrow();

        CtClass<?> readerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
            .filter(c -> c.getSimpleName().equals("PipedReader"))
            .findFirst()
            .orElseThrow();

        MethodRefinementContract writerConnect = maps.getMethodContract(writerClass, "connect", 1);
        assertNotNull(writerConnect);
        assertNull(writerConnect.getMethodRefinement());
        assertNull(writerConnect.getParameterRefinement("reader"));
        assertEquals("this.isConnected == false && reader.isConnected == false", writerConnect.getCombinedPrecondition());
        assertEquals("this.isConnected == true && reader.isConnected == true", writerConnect.getCombinedPostcondition());

        MethodRefinementContract writerWrite = maps.getMethodContract(writerClass, "write", 1);
        assertNotNull(writerWrite);
        assertNull(writerWrite.getMethodRefinement());
        assertNull(writerWrite.getParameterRefinement("s"));
        assertEquals("this.isConnected == false && reader.isConnected == false", writerWrite.getCombinedPrecondition());
        assertEquals("this.isConnected == true && reader.isConnected == true", writerWrite.getCombinedPostcondition());

        MethodRefinementContract readerConnect = maps.getMethodContract(readerClass, "connect", 1);
        assertNotNull(readerConnect);
        assertNull(readerConnect.getMethodRefinement());
        assertNull(readerConnect.getParameterRefinement("writer"));
        assertEquals("this.isConnected == false && writer.isConnected == false", readerConnect.getCombinedPrecondition());
        assertEquals("this.isConnected == true && writer.isConnected == true", readerConnect.getCombinedPostcondition());

        MethodRefinementContract readerRead = maps.getMethodContract(readerClass, "read", 0);
        assertNotNull(readerRead);
        assertNull(readerRead.getMethodRefinement());
        assertEquals("this.isConnected == true", readerRead.getCombinedPrecondition());
        assertEquals("this.isConnected == true", readerRead.getCombinedPostcondition());

        MethodRefinementContract writerConstructor = maps.getConstructorContract(writerClass, 0);
        assertNotNull(writerConstructor);
        assertEquals("this.isConnected == false", writerConstructor.getCombinedPostcondition());

        MethodRefinementContract readerConstructor = maps.getConstructorContract(readerClass, 0);
        assertNotNull(readerConstructor);
        assertEquals("this.isConnected == false", readerConstructor.getCombinedPostcondition());

        CtAnnotation<? extends Annotation> writerFieldRefinement = maps.getFieldRefinement("isConnected", writerClass.getReference());
        assertNull(writerFieldRefinement);

        CtAnnotation<? extends Annotation> readerFieldRefinement = maps.getFieldRefinement("isConnected", readerClass.getReference());
        assertNull(readerFieldRefinement);
    }
}