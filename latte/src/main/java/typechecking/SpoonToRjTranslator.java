package typechecking;

import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import spoon.reflect.code.CtLiteral;

public class SpoonToRjTranslator {
	public static <T> Expression toRjLiteral(CtLiteral<T> literal) {
		Object value = literal.getValue();
		// Needs to be replaced with a switch expression once we are on Java 21
		if (value instanceof Boolean b) return new LiteralBoolean(b);
		if (value instanceof Integer i) return new LiteralInt(i);
		if (value instanceof Double d) return new LiteralReal(d);
		if (value instanceof String s)  return new LiteralString(s);
		return null;
	}
}
