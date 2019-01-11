package it.unibz.inf.ontop.model.term.functionsymbol.impl;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.model.term.Constant;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.RDFTermTypeConstant;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.type.*;
import java.util.Optional;

/**
 * Takes a RDF type term as input.
 * Returns
 *   * NULL if it is not a literal
 *   * "" if the literal type does not have a language tag
 *   * the language tag if available
 */
public class LangTagFunctionSymbolImpl extends FunctionSymbolImpl {

    private final DBTermType dbStringType;

    protected LangTagFunctionSymbolImpl(MetaRDFTermType metaRDFTermType, DBTermType dbStringType) {
        super("LANG_TAG", ImmutableList.of(metaRDFTermType));
        this.dbStringType = dbStringType;
    }

    @Override
    protected boolean mayReturnNullWithoutNullArguments() {
        return true;
    }

    @Override
    protected boolean isAlwaysInjective() {
        return false;
    }

    @Override
    public Optional<TermTypeInference> inferType(ImmutableList<? extends ImmutableTerm> terms) {
        return Optional.of(TermTypeInference.declareTermType(dbStringType));
    }

    @Override
    public boolean canBePostProcessed(ImmutableList<? extends ImmutableTerm> arguments) {
        return true;
    }

    @Override
    protected ImmutableTerm buildTermAfterEvaluation(ImmutableList<ImmutableTerm> newTerms,
                                                     boolean isInConstructionNodeInOptimizationPhase,
                                                     TermFactory termFactory, VariableNullability variableNullability) {
        ImmutableTerm newTerm = newTerms.get(0);
        if (newTerm instanceof RDFTermTypeConstant) {
            RDFTermType termType = ((RDFTermTypeConstant) newTerm).getRDFTermType();
            return Optional.of(termType)
                    .filter(t -> t instanceof RDFDatatype)
                    .map(t -> ((RDFDatatype) t).getLanguageTag()
                            .map(LanguageTag::getFullString)
                            .orElse(""))
                    .map(s -> (Constant) termFactory.getDBStringConstant(s))
                    .orElseGet(termFactory::getNullConstant);
        }
        // TODO: simplify in the presence of magic numbers
        return super.buildTermAfterEvaluation(newTerms, isInConstructionNodeInOptimizationPhase, termFactory, variableNullability);
    }

}
