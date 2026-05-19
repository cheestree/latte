package helpers;

import java.io.File;

import spoon.Launcher;
import spoon.reflect.CtModel;

public class TestModelHelper {
    public static CtModel loadModel(String resourcePath) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new File(resourcePath).getAbsolutePath());
        return launcher.buildModel();
    }
}
