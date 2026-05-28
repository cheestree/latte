package rj_language.smt;

import java.util.Arrays;
import java.util.Objects;

import com.microsoft.z3.Sort;

public class FunctionSignature {
    private final String name;
    private final Sort returnSort;
    private final Sort[] argSorts;

    public FunctionSignature(String name, Sort returnSort, Sort[] argSorts) {
        this.name = name;
        this.returnSort = returnSort;
        this.argSorts = argSorts;
    }

    public String getName() {
        return name;
    }

    public Sort getReturnSort() {
        return returnSort;
    }

    public Sort[] getArgSorts() {
        return argSorts;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FunctionSignature other)) {
            return false;
        }
        return Objects.equals(name, other.name)
            && Objects.equals(returnSort, other.returnSort)
            && Arrays.equals(argSorts, other.argSorts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, returnSort, Arrays.hashCode(argSorts));
    }
}
