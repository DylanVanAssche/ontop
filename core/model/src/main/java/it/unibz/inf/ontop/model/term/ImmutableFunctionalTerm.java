package it.unibz.inf.ontop.model.term;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.model.term.functionsymbol.FunctionSymbol;
import it.unibz.inf.ontop.model.type.TermTypeInference;
import it.unibz.inf.ontop.utils.VariableGenerator;

import java.util.Optional;

/**
 * Functional term that is declared as immutable.
 *
 */
public interface ImmutableFunctionalTerm extends NonVariableTerm, NonConstantTerm {

    ImmutableList<? extends ImmutableTerm> getTerms();

    ImmutableTerm getTerm(int index);

    FunctionSymbol getFunctionSymbol();

    int getArity();

    ImmutableSet<Variable> getVariables();

    @Override
    default Optional<TermTypeInference> inferType() {
        FunctionSymbol functionSymbol = getFunctionSymbol();
        return functionSymbol.inferType(getTerms());
    }

    /**
     * Returns true if it can be post-processed modulo some decomposition
     * (i.e. some sub-terms may not post-processed, but the top function symbol yes)
     */
    boolean canBePostProcessed();

    /**
     * Returns an empty optional when no decomposition is possible
     */
    Optional<InjectivityDecomposition> analyzeInjectivity(ImmutableSet<Variable> nonFreeVariables,
                                                          VariableNullability variableNullability,
                                                          VariableGenerator variableGenerator);

    interface InjectivityDecomposition {

        /**
         * Part of the functional that is injective
         */
        ImmutableFunctionalTerm getInjectiveTerm();

        /**
         * Contains the sub-terms that are not injective.
         * For each of them, a fresh variable has been created.
         */
        Optional<ImmutableMap<Variable, ImmutableTerm>> getSubTermSubstitutionMap();
    }
}
