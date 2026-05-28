package extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import context.ClassLevelMaps;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.visitor.filter.TypeFilter;
import utils.Constants;
import helpers.TestModelHelper;

public class GhostAnnotationTest {
    @Test
    public void testGhostMetadataRecorded() {
        String source = "./src/test/examples/refinements/PipedWriterCorrect.java";
        var model = TestModelHelper.loadModel(source);
        ClassLevelMaps maps = TestModelHelper.getLastMaps();

        CtClass<?> writerClass = model.getElements(new TypeFilter<>(CtClass.class)).stream()
            .filter(c -> c.getSimpleName().equals("PipedWriter"))
            .findFirst()
            .orElseThrow();

        CtField<?> field = writerClass.getFields().stream()
            .filter(f -> f.getSimpleName().equals("isConnected"))
            .findFirst()
            .orElseThrow();

        assertNotNull(field.getMetadata(Constants.FIELD_GHOST_KEY));
        assertEquals(Boolean.TRUE, field.getMetadata(Constants.FIELD_GHOST_KEY));
        assertTrue(maps.isGhostField(writerClass.getSimpleName(), "isConnected"));
    }
}
