package helpers;

import java.io.File;

import spoon.Launcher;
import spoon.reflect.CtModel;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
import typechecking.LatteClassFirstPass;
import typechecking.LatteTypeChecker;
import typechecking.RefinementFirstPass;

public class TestModelHelper {
    private static ClassLevelMaps lastMaps;

    public static CtModel loadModel(String resourcePath) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new File(resourcePath).getAbsolutePath());
        CtModel model = launcher.buildModel();

        SymbolicEnvironment se = new SymbolicEnvironment();
        PermissionEnvironment pe = new PermissionEnvironment();
        ClassLevelMaps maps = new ClassLevelMaps();
        model.getRootPackage().accept(new LatteClassFirstPass(se, pe, maps));
        model.getRootPackage().accept(new LatteTypeChecker(se, pe, maps));
        model.getRootPackage().accept(new RefinementFirstPass(se, pe, maps));
        lastMaps = maps;

        return model;
    }

    public static ClassLevelMaps getLastMaps() {
        return lastMaps;
    }
}
