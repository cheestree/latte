package typechecking.contracts;

import specification.Shared;

public class InvalidReturnType {
	static class Expected {
	}

	static class Actual {
	}

	@Shared
	Expected returnWrongType(@Shared Actual value) {
		return value;
	}
}
