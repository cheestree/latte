package typechecking.cases;

import specification.Shared;

public class SharedConstructorArgumentPasses {
    void acceptsSharedArgument(@Shared Object shared) {
        new Box(shared);
    }

    static class Box {
        Box(@Shared Object value) {

        }
    }
}
