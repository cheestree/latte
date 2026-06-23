package typechecking.cases;

public class ExpressionVisitorCases {
	void literals() {
		int integerValue = 42;
		boolean booleanValue = true;
		double realValue = 3.5;
		String stringValue = "latte";
		Object nullValue = null;
	}

	void unary() {
		int negative = -42;
		boolean negated = !true;
	}

	void binary() {
		int sum = 1 + 2;
		boolean comparison = 1 < 2;
		boolean conjunction = true && false;
	}

	void variables(int parameter) {
		int local = parameter;
		int copy = local;
	}

	Object thisAccess() {
		return this;
	}

	void caughtException() {
		try {
			throw new RuntimeException();
		} catch (RuntimeException exception) {
			RuntimeException copy = exception;
		}
	}
}
