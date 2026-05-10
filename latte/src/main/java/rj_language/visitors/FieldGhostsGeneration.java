package rj_language.visitors;


import context.ClassLevelMaps;
import spoon.reflect.declaration.CtField;
import spoon.reflect.visitor.CtScanner;

public class FieldGhostsGeneration extends CtScanner {
    private final ClassLevelMaps mtc;

    public FieldGhostsGeneration(ClassLevelMaps mtc) {
        this.mtc = mtc;
    }

    @Override
    public <T> void visitCtField(CtField<T> field) {
        boolean isGhost = field.getAnnotations().stream()
            .anyMatch(a -> a.getAnnotationType().getSimpleName().equals("Ghost"));
        if (isGhost)
            // Add the ghost field to the map
            /*
            mtc.addGhostField(
                field.getDeclaringType().getQualifiedName(),
                field.getSimpleName()
            );
            */
        super.visitCtField(field);
    }
}