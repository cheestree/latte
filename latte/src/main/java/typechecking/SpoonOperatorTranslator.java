package typechecking;

import rj_language.ast.BinaryOperator;
import rj_language.ast.UnaryOperator;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.UnaryOperatorKind;

final class SpoonOperatorTranslator {
	private SpoonOperatorTranslator() {
	}

	static BinaryOperator toRj(BinaryOperatorKind kind) {
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
			default -> null;
		};
	}

	static UnaryOperator toRj(UnaryOperatorKind kind) {
		return switch (kind) {
			case NEG -> UnaryOperator.NEGATE;
			case NOT -> UnaryOperator.NOT;
			default -> null;
		};
	}
}
