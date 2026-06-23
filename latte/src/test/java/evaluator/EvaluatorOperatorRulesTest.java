package evaluator;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import context.ClassLevelMaps;
import context.SymbolicValue;
import rj_language.ast.Expression;
import rj_language.parsing.RefinementsParser;
import typechecking.Evaluator;

public class EvaluatorOperatorRulesTest extends EvaluatorTestSupport {
	@ParameterizedTest
	@MethodSource("literalExpressions")
	void evalLiteralCreatesFreshImmutableValueAndPathCondition(String source, String expectedLiteral) throws Exception {
		Evaluator evaluator = new Evaluator(new ClassLevelMaps(), typeEnv, symbEnv, permEnv, refinementPath);

		Expression result = evaluator.eval(RefinementsParser.createAST(source));
		SymbolicValue resultValue = symbolicValueOf(result);

		assertImmutable(resultValue);
		assertPathEquals(resultValue + " == " + expectedLiteral);
	}

	@ParameterizedTest
	@MethodSource("unaryExpressions")
	void evalUnaryCreatesFreshImmutableValueAndPathCondition(String source, String expectedPathCondition) throws Exception {
		Evaluator evaluator = new Evaluator(new ClassLevelMaps(), typeEnv, symbEnv, permEnv, refinementPath);

		Expression result = evaluator.eval(RefinementsParser.createAST(source));
		SymbolicValue resultValue = symbolicValueOf(result);

		assertImmutable(resultValue);
		assertPathEquals(expectedPathCondition);
	}

	@ParameterizedTest
	@MethodSource("binaryExpressions")
	void evalBinaryCreatesFreshImmutableValueAndPathCondition(String source, String expectedPathCondition) throws Exception {
		Evaluator evaluator = new Evaluator(new ClassLevelMaps(), typeEnv, symbEnv, permEnv, refinementPath);

		Expression result = evaluator.eval(RefinementsParser.createAST(source));
		SymbolicValue resultValue = symbolicValueOf(result);

		assertImmutable(resultValue);
		assertPathEquals(expectedPathCondition);
	}

	private static Stream<Arguments> literalExpressions() {
		return Stream.of(
			Arguments.of("true", "true"),
			Arguments.of("42", "42"),
			Arguments.of("3.5", "3.5"),
			Arguments.of("\"latte\"", "\"latte\"")
		);
	}

	private static Stream<Arguments> unaryExpressions() {
		return Stream.of(
			Arguments.of("!true", "𝜈0 == true && 𝜈1 == !𝜈0"),
			Arguments.of("-42", "𝜈0 == 42 && 𝜈1 == -𝜈0")
		);
	}

	private static Stream<Arguments> binaryExpressions() {
		return Stream.of(
			Arguments.of("1 + 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 + 𝜈1"),
			Arguments.of("1 - 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 - 𝜈1"),
			Arguments.of("1 * 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 * 𝜈1"),
			Arguments.of("1 / 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 / 𝜈1"),
			Arguments.of("1 % 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 % 𝜈1"),
			Arguments.of("1 < 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 < 𝜈1"),
			Arguments.of("1 > 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 > 𝜈1"),
			Arguments.of("1 <= 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 <= 𝜈1"),
			Arguments.of("1 >= 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 >= 𝜈1"),
			Arguments.of("1 == 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 == 𝜈1"),
			Arguments.of("1 != 2", "𝜈0 == 1 && 𝜈1 == 2 && 𝜈2 == 𝜈0 != 𝜈1"),
			Arguments.of("true && false", "𝜈0 == true && 𝜈1 == false && 𝜈2 == 𝜈0 && 𝜈1"),
			Arguments.of("true || false", "𝜈0 == true && 𝜈1 == false && 𝜈2 == 𝜈0 || 𝜈1")
		);
	}
}
