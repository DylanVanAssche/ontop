package it.unibz.inf.ontop.answering.reformulation.generation.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import it.unibz.inf.ontop.answering.reformulation.generation.IQTree2NativeNodeGenerator;
import it.unibz.inf.ontop.answering.reformulation.generation.algebra.IQTree2SelectFromWhereConverter;
import it.unibz.inf.ontop.answering.reformulation.generation.algebra.SelectFromWhere;
import it.unibz.inf.ontop.answering.reformulation.generation.serializer.SelectFromWhereSerializer;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.node.NativeNode;
import it.unibz.inf.ontop.iq.type.UniqueTermTypeExtractor;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.type.DBTermType;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

public class DefaultSQLIQTree2NativeNodeGenerator implements IQTree2NativeNodeGenerator {

    private final SelectFromWhereSerializer serializer;
    private final IQTree2SelectFromWhereConverter converter;
    private final IntermediateQueryFactory iqFactory;
    private final UniqueTermTypeExtractor uniqueTermTypeExtractor;

    @Inject
    private DefaultSQLIQTree2NativeNodeGenerator(SelectFromWhereSerializer serializer,
                                                 IQTree2SelectFromWhereConverter converter,
                                                 IntermediateQueryFactory iqFactory,
                                                 UniqueTermTypeExtractor uniqueTermTypeExtractor) {
        this.serializer = serializer;
        this.converter = converter;
        this.iqFactory = iqFactory;
        this.uniqueTermTypeExtractor = uniqueTermTypeExtractor;
    }


    @Override
    public NativeNode generate(IQTree iqTree) {
        ImmutableSortedSet<Variable> signature = ImmutableSortedSet.copyOf(iqTree.getVariables());

        SelectFromWhere selectFromWhere = converter.convert(iqTree);
        String sqlQuery = serializer.serialize(selectFromWhere);

        ImmutableMap<Variable, DBTermType> variableTypeMap = extractVariableTypeMap(iqTree);

        return iqFactory.createNativeNode(signature, variableTypeMap, sqlQuery, iqTree.getVariableNullability());
    }

    private ImmutableMap<Variable, DBTermType> extractVariableTypeMap(IQTree tree) {
        return tree.getVariables().stream()
                .collect(ImmutableCollectors.toMap(
                        v -> v,
                        v -> extractUniqueKnownType(v, tree)));
    }

    private DBTermType extractUniqueKnownType(Variable v, IQTree tree) {
        return uniqueTermTypeExtractor.extractUniqueTermType(v, tree)
                .filter(t -> t instanceof DBTermType)
                .map(t -> (DBTermType) t)
                .orElseThrow(() -> new MinorOntopInternalBugException(
                        "Was expecting an unique and known DB term type to be extracted " +
                                "for the SQL variable " + v));
    }
}
