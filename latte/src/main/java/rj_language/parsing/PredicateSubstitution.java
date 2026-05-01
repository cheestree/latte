package rj_language.parsing;

import rj_language.ast.Expression;
import rj_language.visitors.ExpressionPrettyPrinter;
import rj_language.visitors.ExpressionSubstitutionVisitor;

/**
 * Utility for predicate substitution rho[e/x].
 */
public final class PredicateSubstitution {
    private PredicateSubstitution() {
    }

    public static Expression substitute(Expression rho, String x, Expression e) {
        return ExpressionSubstitutionVisitor.substitute(rho, x, e);
    }

    public static Expression substitute(String rho, String x, String e) throws ParsingException {
        Expression rhoAst = RefinementsParser.createAST(rho);
        Expression eAst = RefinementsParser.createAST(e);
        return substitute(rhoAst, x, eAst);
    }

    public static String substituteToString(String rho, String x, String e) throws ParsingException {
        return ExpressionPrettyPrinter.print(substitute(rho, x, e));
    }
}