package it.unibz.inf.ontop.iq.type.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unibz.inf.ontop.dbschema.Attribute;
import it.unibz.inf.ontop.dbschema.RelationDefinition;
import it.unibz.inf.ontop.exception.NonUniqueTermTypeException;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.LeafIQTree;
import it.unibz.inf.ontop.iq.node.*;
import it.unibz.inf.ontop.iq.visit.IQVisitor;
import it.unibz.inf.ontop.model.atom.DataAtom;
import it.unibz.inf.ontop.model.atom.RelationPredicate;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.NonVariableTerm;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.term.VariableOrGroundTerm;
import it.unibz.inf.ontop.model.type.TermType;
import it.unibz.inf.ontop.iq.type.UniqueTermTypeExtractor;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import java.util.Optional;
import java.util.stream.IntStream;

@Singleton
public class BasicUniqueTermTypeExtractor implements UniqueTermTypeExtractor {

    @Inject
    private BasicUniqueTermTypeExtractor() {
    }

    @Override
    public Optional<TermType> extractUniqueTermType(ImmutableTerm term, IQTree subTree) {
        return (term instanceof Variable)
                ? extractTypeFromVariable((Variable)term, subTree)
                : extractType((NonVariableTerm) term, subTree);
    }

    private Optional<TermType> extractTypeFromVariable(Variable variable, IQTree subTree) {
        return subTree.acceptVisitor(new TermTypeVariableVisitor(variable, this));
    }

    private Optional<TermType> extractType(NonVariableTerm nonVariableTerm, IQTree subTree) {
        return nonVariableTerm.inferType()
                .flatMap(i -> i.getTermType()
                        .map(Optional::of)
                        // Continues on a type of a variable defined in the sub-tree
                        .orElseGet(() -> i.getRedirectionVariable()
                                .flatMap(v -> extractTypeFromVariable(v, subTree))));
    }


    protected static class TermTypeVariableVisitor implements IQVisitor<Optional<TermType>> {

        protected final Variable variable;
        protected final UniqueTermTypeExtractor typeExtractor;

        protected TermTypeVariableVisitor(Variable variable, UniqueTermTypeExtractor typeExtractor) {
            this.variable = variable;
            this.typeExtractor = typeExtractor;
        }

        @Override
        public Optional<TermType> visitIntensionalData(IntensionalDataNode dataNode) {
            return Optional.empty();
        }

        @Override
        public Optional<TermType> visitExtensionalData(ExtensionalDataNode dataNode) {
            RelationDefinition relationDefinition = dataNode.getRelationDefinition();

            return dataNode.getArgumentMap().entrySet().stream()
                    .filter(e -> e.getValue().equals(variable))
                    .map(e -> relationDefinition.getAttribute(e.getKey() + 1))
                    .map(Attribute::getTermType)
                    .filter(Optional::isPresent)
                    .map(o -> (TermType) o.get())
                    .findAny();
        }

        @Override
        public Optional<TermType> visitEmpty(EmptyNode node) {
            return Optional.empty();
        }

        @Override
        public Optional<TermType> visitTrue(TrueNode node) {
            return Optional.empty();
        }

        @Override
        public Optional<TermType> visitNative(NativeNode nativeNode) {
            return Optional.ofNullable(nativeNode.getTypeMap().get(variable));
        }

        @Override
        public Optional<TermType> visitNonStandardLeafNode(LeafIQTree leafNode) {
            return Optional.empty();
        }

        @Override
        public Optional<TermType> visitConstruction(ConstructionNode rootNode, IQTree child) {
            return visitExtendedProjection(rootNode, child);
        }

        @Override
        public Optional<TermType> visitAggregation(AggregationNode rootNode, IQTree child) {
            return visitExtendedProjection(rootNode, child);
        }

        protected Optional<TermType> visitExtendedProjection(ExtendedProjectionNode rootNode, IQTree child) {
            return typeExtractor.extractUniqueTermType(rootNode.getSubstitution().apply(variable), child);
        }

        @Override
        public Optional<TermType> visitFilter(FilterNode rootNode, IQTree child) {
            return child.acceptVisitor(this);
        }

        @Override
        public Optional<TermType> visitDistinct(DistinctNode rootNode, IQTree child) {
            return child.acceptVisitor(this);
        }

        @Override
        public Optional<TermType> visitSlice(SliceNode sliceNode, IQTree child) {
            return child.acceptVisitor(this);
        }

        @Override
        public Optional<TermType> visitOrderBy(OrderByNode rootNode, IQTree child) {
            return child.acceptVisitor(this);
        }

        @Override
        public Optional<TermType> visitNonStandardUnaryNode(UnaryOperatorNode rootNode, IQTree child) {
            return Optional.empty();
        }

        /**
         * Only consider the right child for right-specific variables
         */
        @Override
        public Optional<TermType> visitLeftJoin(LeftJoinNode rootNode, IQTree leftChild, IQTree rightChild) {
            if (leftChild.getVariables().contains(variable)) {
                return leftChild.acceptVisitor(this);
            }
            else if (rightChild.getVariables().contains(variable)) {
                return rightChild.acceptVisitor(this);
            }
            else
                return Optional.empty();
        }

        @Override
        public Optional<TermType> visitNonStandardBinaryNonCommutativeNode(BinaryNonCommutativeOperatorNode rootNode,
                                                                           IQTree leftChild, IQTree rightChild) {
            return Optional.empty();
        }

        /**
         * Returns the first type found for a variable.
         *
         * If multiple types could have been found -> not matching -> will be later eliminated
         *
         */
        @Override
        public Optional<TermType> visitInnerJoin(InnerJoinNode rootNode, ImmutableList<IQTree> children) {
            return children.stream()
                    .map(c -> c.acceptVisitor(this))
                    .filter(Optional::isPresent)
                    .findAny()
                    .orElse(Optional.empty());
        }

        @Override
        public Optional<TermType> visitUnion(UnionNode rootNode, ImmutableList<IQTree> children) {
            ImmutableSet<TermType> termTypes = children.stream()
                    .map(c -> c.acceptVisitor(this))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(ImmutableCollectors.toSet());

            if (termTypes.size() > 1)
                throw new NonUniqueTermTypeException(String.format("Multiple term types found for %s: %s",
                        variable, termTypes));

            return termTypes.stream()
                    .findAny();
        }

        @Override
        public Optional<TermType> visitNonStandardNaryNode(NaryOperatorNode rootNode, ImmutableList<IQTree> children) {
            return Optional.empty();
        }
    }
}
