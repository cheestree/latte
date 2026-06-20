package rj_language.visitors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rj_language.ast.BinaryExpression;
import rj_language.ast.BinaryOperator;
import rj_language.ast.FieldAccess;
import rj_language.ast.FunctionInvocation;
import rj_language.ast.LiteralBoolean;
import rj_language.ast.ReturnExpression;
import rj_language.ast.Var;

class SubstitutionVisitorTest {
	@Nested
	class ReturnSubstitution {
		@Test
		void replacesReturnExpression() {
			Var replacement = new Var("result");

			var substituted = new ReturnExpression().accept(new SubstitutionVisitor("return", replacement));

			assertSame(replacement, substituted);
		}

		@Test
		void replacesReturnInsideBinaryExpression() {
			var expression = new BinaryExpression(
				new ReturnExpression(),
				BinaryOperator.EQ,
				new Var("expected"));

			var substituted = expression.accept(new SubstitutionVisitor("return", new Var("value")));

			assertEquals("value == expected", ExpressionPrettyPrinter.print(substituted));
		}
	}

	@Nested
	class VariableSubstitution {
		@Test
		void replacesVariableInsideFieldReceiver() {
			var expression = new FieldAccess(new Var("receiver"), "field");

			var substituted = expression.accept(new SubstitutionVisitor("receiver", new Var("replacement")));

			assertEquals("replacement.field", ExpressionPrettyPrinter.print(substituted));
		}

		@Test
		void leavesUnmatchedVariablesAndLiteralsUnchanged() {
			Var variable = new Var("other");
			LiteralBoolean literal = new LiteralBoolean(true);
			SubstitutionVisitor visitor = new SubstitutionVisitor("target", new Var("replacement"));

			assertSame(variable, variable.accept(visitor));
			assertSame(literal, literal.accept(visitor));
		}
	}

	@Test
	void rejectsFunctionInvocationSubstitution() {
		FunctionInvocation invocation = new FunctionInvocation("predicate", java.util.List.of());

		assertThrows(
			UnsupportedOperationException.class,
			() -> invocation.accept(new SubstitutionVisitor("return", new Var("value")))
		);
	}
}
