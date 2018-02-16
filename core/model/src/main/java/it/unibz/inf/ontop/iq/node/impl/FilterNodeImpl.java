package it.unibz.inf.ontop.iq.node.impl;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import it.unibz.inf.ontop.datalog.impl.DatalogTools;
import it.unibz.inf.ontop.evaluator.ExpressionEvaluator;
import it.unibz.inf.ontop.evaluator.TermNullabilityEvaluator;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.exception.QueryNodeTransformationException;
import it.unibz.inf.ontop.iq.node.*;
import it.unibz.inf.ontop.iq.node.normalization.ConditionSimplifier.ExpressionAndSubstitution;
import it.unibz.inf.ontop.iq.node.normalization.ConditionSimplifier;
import it.unibz.inf.ontop.iq.node.normalization.FilterNormalizer;
import it.unibz.inf.ontop.iq.transform.IQTransformer;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.impl.ImmutabilityTools;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.iq.*;
import it.unibz.inf.ontop.iq.transform.node.HeterogeneousQueryNodeTransformer;
import it.unibz.inf.ontop.iq.transform.node.HomogeneousQueryNodeTransformer;
import it.unibz.inf.ontop.iq.exception.InvalidIntermediateQueryException;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.substitution.impl.ImmutableSubstitutionTools;
import it.unibz.inf.ontop.substitution.impl.ImmutableUnificationTools;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.VariableGenerator;

import java.util.Optional;


public class FilterNodeImpl extends JoinOrFilterNodeImpl implements FilterNode {

    private static final String FILTER_NODE_STR = "FILTER";
    private final ConstructionNodeTools constructionNodeTools;
    private final ConditionSimplifier conditionSimplifier;
    private final FilterNormalizer normalizer;

    @AssistedInject
    private FilterNodeImpl(@Assisted ImmutableExpression filterCondition, TermNullabilityEvaluator nullabilityEvaluator,
                           TermFactory termFactory, TypeFactory typeFactory, DatalogTools datalogTools,
                           ImmutabilityTools immutabilityTools, SubstitutionFactory substitutionFactory,
                           ImmutableUnificationTools unificationTools, ImmutableSubstitutionTools substitutionTools,
                           ExpressionEvaluator defaultExpressionEvaluator, IntermediateQueryFactory iqFactory,
                           ConstructionNodeTools constructionNodeTools, ConditionSimplifier conditionSimplifier,
                           FilterNormalizer normalizer) {
        super(Optional.of(filterCondition), nullabilityEvaluator, termFactory, iqFactory, typeFactory, datalogTools,
                immutabilityTools, substitutionFactory, unificationTools, substitutionTools, defaultExpressionEvaluator);
        this.constructionNodeTools = constructionNodeTools;
        this.conditionSimplifier = conditionSimplifier;
        this.normalizer = normalizer;
    }

    @Override
    public void acceptVisitor(QueryNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public FilterNode clone() {
        return iqFactory.createFilterNode(getFilterCondition());
    }

    @Override
    public FilterNode acceptNodeTransformer(HomogeneousQueryNodeTransformer transformer) throws QueryNodeTransformationException {
        return transformer.transform(this);
    }

    @Override
    public NodeTransformationProposal acceptNodeTransformer(HeterogeneousQueryNodeTransformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public ImmutableExpression getFilterCondition() {
        return getOptionalFilterCondition().get();
    }

    @Override
    public FilterNode changeFilterCondition(ImmutableExpression newFilterCondition) {
        return iqFactory.createFilterNode(newFilterCondition);
    }

    @Override
    public boolean isVariableNullable(IntermediateQuery query, Variable variable) {
        if (isFilteringNullValue(variable))
            return false;

        return query.getFirstChild(this)
                .map(c -> c.isVariableNullable(query, variable))
                .orElseThrow(() -> new InvalidIntermediateQueryException("A filter node must have a child"));
    }

    @Override
    public ImmutableSet<Variable> getNullableVariables(IQTree child) {
        return child.getNullableVariables().stream()
                .filter(v -> !isFilteringNullValue(v))
                .collect(ImmutableCollectors.toSet());
    }


    @Override
    public IQTree liftIncompatibleDefinitions(Variable variable, IQTree child) {
        IQTree newChild = child.liftIncompatibleDefinitions(variable);
        QueryNode newChildRoot = newChild.getRootNode();

        /*
         * Lift the union above the filter
         */
        if ((newChildRoot instanceof UnionNode)
                && ((UnionNode) newChildRoot).hasAChildWithLiftableDefinition(variable, newChild.getChildren())) {
            UnionNode unionNode = (UnionNode) newChildRoot;
            ImmutableList<IQTree> grandChildren = newChild.getChildren();

            ImmutableList<IQTree> newChildren = grandChildren.stream()
                    .map(c -> (IQTree) iqFactory.createUnaryIQTree(this, c))
                    .collect(ImmutableCollectors.toList());

            return iqFactory.createNaryIQTree(unionNode, newChildren);
        }
        return iqFactory.createUnaryIQTree(this, newChild);
    }

    @Override
    public IQTree propagateDownConstraint(ImmutableExpression constraint, IQTree child) {
        return propagateDownCondition(child, Optional.of(constraint));
    }

    private IQTree propagateDownCondition(IQTree child, Optional<ImmutableExpression> initialConstraint) {
        try {
            // TODO: also consider the constraint for simplifying the condition
            ExpressionAndSubstitution conditionSimplificationResults = conditionSimplifier
                    .simplifyCondition(getFilterCondition());

            Optional<ImmutableExpression> downConstraint = conditionSimplifier.computeDownConstraint(initialConstraint,
                    conditionSimplificationResults);

            IQTree newChild = Optional.of(conditionSimplificationResults.getSubstitution())
                    .filter(s -> !s.isEmpty())
                    .map(s -> child.applyDescendingSubstitution(s, downConstraint))
                    .orElseGet(() -> downConstraint
                            .map(child::propagateDownConstraint)
                            .orElse(child));

            IQTree filterLevelTree = conditionSimplificationResults.getOptionalExpression()
                    .map(e -> e.equals(getFilterCondition()) ? this : iqFactory.createFilterNode(e))
                    .map(filterNode -> (IQTree) iqFactory.createUnaryIQTree(filterNode, newChild))
                    .orElse(newChild);

            return Optional.of(conditionSimplificationResults.getSubstitution())
                    .filter(s -> !s.isEmpty())
                    .map(s -> (ImmutableSubstitution<ImmutableTerm>)(ImmutableSubstitution<?>)s)
                    .map(s -> iqFactory.createConstructionNode(child.getVariables(), s))
                    .map(c -> (IQTree) iqFactory.createUnaryIQTree(c, filterLevelTree))
                    .orElse(filterLevelTree);


        } catch (UnsatisfiableConditionException e) {
            return iqFactory.createEmptyNode(child.getVariables());
        }

    }

    @Override
    public IQTree acceptTransformer(IQTree tree, IQTransformer transformer, IQTree child) {
        return transformer.transformFilter(tree,this, child);
    }

    @Override
    public void validateNode(IQTree child) throws InvalidIntermediateQueryException {
        checkExpression(getFilterCondition(), ImmutableList.of(child));
    }

    @Override
    public boolean isConstructed(Variable variable, IQTree child) {
        return child.isConstructed(variable);
    }

    /**
     * TODO: detect minus encodings
     */
    @Override
    public boolean isDistinct(IQTree child) {
        return child.isDistinct();
    }

    @Override
    public boolean isSyntacticallyEquivalentTo(QueryNode node) {
        return (node instanceof FilterNode)
                && ((FilterNode) node).getFilterCondition().equals(this.getFilterCondition());
    }

    @Override
    public ImmutableSet<Variable> getRequiredVariables(IntermediateQuery query) {
        return getLocallyRequiredVariables();
    }

    @Override
    public boolean isEquivalentTo(QueryNode queryNode) {
        return (queryNode instanceof FilterNode)
                && getFilterCondition().equals(((FilterNode) queryNode).getFilterCondition());
    }

    @Override
    public String toString() {
        return FILTER_NODE_STR + getOptionalFilterString();
    }

    /**
     * TODO: Optimization: lift direct construction and filter nodes before normalizing them
     *  (so as to reduce the recursive pressure)
     */
    @Override
    public IQTree normalizeForOptimization(IQTree initialChild, VariableGenerator variableGenerator,
                                           IQProperties currentIQProperties) {
        return normalizer.normalizeForOptimization(this, initialChild, variableGenerator, currentIQProperties);
    }

    @Override
    public IQTree applyDescendingSubstitution(
            ImmutableSubstitution<? extends VariableOrGroundTerm> descendingSubstitution,
            Optional<ImmutableExpression> constraint, IQTree child) {

        ImmutableExpression unoptimizedExpression = descendingSubstitution.applyToBooleanExpression(getFilterCondition());

        ImmutableSet<Variable> newlyProjectedVariables = constructionNodeTools
                .computeNewProjectedVariables(descendingSubstitution, child.getVariables());

        try {
            ExpressionAndSubstitution expressionAndSubstitution = conditionSimplifier.simplifyCondition(unoptimizedExpression);

            Optional<ImmutableExpression> downConstraint = conditionSimplifier.computeDownConstraint(constraint,
                    expressionAndSubstitution);

            ImmutableSubstitution<? extends VariableOrGroundTerm> downSubstitution =
                    ((ImmutableSubstitution<VariableOrGroundTerm>)descendingSubstitution)
                            .composeWith2(expressionAndSubstitution.getSubstitution());

            IQTree newChild = child.applyDescendingSubstitution(downSubstitution, downConstraint);
            IQTree filterLevelTree = expressionAndSubstitution.getOptionalExpression()
                    .map(iqFactory::createFilterNode)
                    .map(n -> (IQTree) iqFactory.createUnaryIQTree(n, newChild))
                    .orElse(newChild);
            return expressionAndSubstitution.getSubstitution().isEmpty()
                    ? filterLevelTree
                    : iqFactory.createUnaryIQTree(
                            iqFactory.createConstructionNode(newlyProjectedVariables,
                                    (ImmutableSubstitution<ImmutableTerm>)(ImmutableSubstitution<?>)
                                            expressionAndSubstitution.getSubstitution()),
                            filterLevelTree);
        } catch (UnsatisfiableConditionException e) {
            return iqFactory.createEmptyNode(newlyProjectedVariables);
        }

    }
}
