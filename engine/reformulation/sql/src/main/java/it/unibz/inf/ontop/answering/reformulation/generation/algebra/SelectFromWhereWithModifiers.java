package it.unibz.inf.ontop.answering.reformulation.generation.algebra;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import it.unibz.inf.ontop.iq.node.OrderByNode;
import it.unibz.inf.ontop.model.term.ImmutableExpression;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;

import java.util.Optional;

/**
 * TODO: find a better name
 */
public interface SelectFromWhereWithModifiers extends SQLExpression {

    ImmutableSortedSet<Variable> getProjectedVariables();

    ImmutableSubstitution<? extends ImmutableTerm> getSubstitution();

    /**
     * Implicit inner join between them
     */
    ImmutableList<? extends SQLExpression> getFromSQLExpressions();

    Optional<ImmutableExpression> getWhereExpression();

    boolean isDistinct();
    Optional<Long> getLimit();
    Optional<Long> getOffset();

    ImmutableList<OrderByNode.OrderComparator> getSortConditions();

    default boolean hasOrder() {
        return !getSortConditions().isEmpty();
    }

}
