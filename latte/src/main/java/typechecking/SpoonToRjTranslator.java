package typechecking;

import rj_language.ast.BinaryOperator;
import rj_language.ast.Expression;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.LiteralInt;
import rj_language.ast.LiteralReal;
import rj_language.ast.LiteralString;
import rj_language.ast.UnaryOperator;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.UnaryOperatorKind;

public class SpoonToRjTranslator {
	public static BinaryOperator toRjBinaryOperator(BinaryOperatorKind kind) {
		return switch (kind) {
			case OR -> BinaryOperator.OR;
			case AND -> BinaryOperator.AND;
			case EQ -> BinaryOperator.EQ;
			case NE -> BinaryOperator.NEQ;
			case LT -> BinaryOperator.LT;
			case LE -> BinaryOperator.LE;
			case GT -> BinaryOperator.GT;
			case GE -> BinaryOperator.GE;
			case PLUS -> BinaryOperator.ADD;
			case MINUS -> BinaryOperator.SUB;
			case MUL -> BinaryOperator.MUL;
			case DIV -> BinaryOperator.DIV;
			case MOD -> BinaryOperator.MOD;
			default  -> null;
		};
	}

	public static UnaryOperator toRjUnaryOperator(UnaryOperatorKind kind) {
		return switch (kind) {
			case NEG -> UnaryOperator.NEGATE;
			case NOT -> UnaryOperator.NOT;
			default  -> null;
		};
	}
	
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
