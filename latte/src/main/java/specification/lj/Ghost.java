package specification.lj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to annotate ghost fields in the code.
 * <p>
 * Ghost annotations are used to indicate that a field is also a ghost variable, which is a variable 
 * that has an old value that can be used in specifications and proofs, but does not exist at runtime.
 * <p>
 * <strong>Example:</strong>
 * <pre>
 * {@code
 * public class MyStack {
 *     // ...
 *     @Ghost
 *     int size
 * }
 * }
 * </pre>
 *
 * @author Catarina Gamboa
 * modified by Daniel Carvalho for a different implementation of ghost variables, now fields are annotated with @Ghost instead of classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Ghost {

    /**
     * The declaration of the ghost variable, for a given field.
     * <p>
     * <strong>Example:</strong>
     * <pre>
     * {@code
     * @Ghost
     * int size
     * }
     * </pre>
     */
    String value();
}
