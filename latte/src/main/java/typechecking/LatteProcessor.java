package typechecking;

import java.util.ArrayList;
import java.util.List;

import context.ClassLevelMaps;
import context.PermissionEnvironment;
import context.SymbolicEnvironment;
import context.TypeEnvironment;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;

public class LatteProcessor extends AbstractProcessor<CtPackage> {

    List<CtPackage> visitedPackages = new ArrayList<>();
    Factory factory;

    public LatteProcessor(Factory factory) {
        this.factory = factory;
    }

    @Override
    public void process(CtPackage pkg) {
        TypeEnvironment te = new TypeEnvironment();
        SymbolicEnvironment se = new SymbolicEnvironment();
        PermissionEnvironment pe = new PermissionEnvironment();
        ClassLevelMaps mtc = new ClassLevelMaps();
        
        if (!visitedPackages.contains(pkg)) {
            visitedPackages.add(pkg);
            pkg.accept(new LatteClassFirstPass( te, se, pe, mtc));
            pkg.accept(new RefinementFirstPass( te, se, pe, mtc));
            pkg.accept(new LatteTypeChecker( te, se, pe, mtc));
        }

    }
    
}