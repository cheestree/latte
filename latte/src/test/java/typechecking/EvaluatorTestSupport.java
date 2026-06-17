package typechecking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import context.PermissionEnvironment;
import context.RefinementPath;
import context.SymbolicEnvironment;

abstract class EvaluatorTestSupport {
    protected SymbolicEnvironment symbEnv;
    protected PermissionEnvironment permEnv;
    protected RefinementPath refinementPath;

    @BeforeEach
    void setEnvironment() {
        symbEnv = new SymbolicEnvironment();
        permEnv = new PermissionEnvironment();
        refinementPath = new RefinementPath();
        symbEnv.enterScope();
        permEnv.enterScope();
    }

    @AfterEach
    void tearDownEnvironment() {
        refinementPath = null;
        permEnv.exitScope();
        symbEnv.exitScope();
    }
}
