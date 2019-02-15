package it.unibz.inf.ontop.answering.reformulation.generation.algebra;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.assistedinject.Assisted;
import it.unibz.inf.ontop.iq.node.OrderByNode;
import it.unibz.inf.ontop.model.term.ImmutableExpression;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;

import java.util.Optional;

/**
 * TODO: complete
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface SQLAlgebraFactory {

    SelectFromWhereWithModifiers createSelectFromWhere(ImmutableSortedSet<Variable> projectedVariables,
                                                       ImmutableSubstitution<? extends ImmutableTerm> substitution,
                                                       @Assisted("fromRelations") ImmutableList<? extends SQLExpression> fromRelations,
                                                       @Assisted("whereExpression") Optional<ImmutableExpression> whereExpression,
                                                       boolean isDistinct,
                                                       @Assisted("limit") Optional<Long> limit,
                                                       @Assisted("offset") Optional<Long> offset,
                                                       @Assisted("sortConditions") ImmutableList<OrderByNode.OrderComparator> sortConditions);

    SQLSerializedQuery createSQLSerializedQuery(String sqlString, ImmutableMap<Variable, String> columnNames);

}
