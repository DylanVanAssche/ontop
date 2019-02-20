package it.unibz.inf.ontop.model.term.functionsymbol.impl;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.iq.tools.TypeConstantDictionary;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.RDFTermTypeFunctionSymbol;
import it.unibz.inf.ontop.model.type.*;

import java.util.stream.Stream;

/**
 * https://www.w3.org/TR/sparql11-query/#ebv
 *
 * Limitation:
 *   - We don't look for invalid lexical form for booleans and numerics
 *
 */
public class LexicalEBVFunctionSymbolImpl extends BooleanFunctionSymbolImpl {


    protected LexicalEBVFunctionSymbolImpl(DBTermType dbStringType, MetaRDFTermType metaRDFTermType,
                                           DBTermType dbBooleanTermType) {
        super("LEX_EBV", ImmutableList.of(dbStringType, metaRDFTermType), dbBooleanTermType);
    }

    @Override
    public boolean blocksNegation() {
        return true;
    }

    @Override
    public ImmutableExpression negate(ImmutableList<? extends ImmutableTerm> subTerms, TermFactory termFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean tolerateNulls() {
        return false;
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
    public boolean canBePostProcessed(ImmutableList<? extends ImmutableTerm> arguments) {
        return false;
    }

    @Override
    protected ImmutableTerm buildTermAfterEvaluation(ImmutableList<ImmutableTerm> newTerms, TermFactory termFactory,
                                                     VariableNullability variableNullability) {
        ImmutableTerm lexicalTerm = newTerms.get(0);
        ImmutableTerm typeTerm = newTerms.get(1);

        if (typeTerm instanceof RDFTermTypeConstant) {
            RDFTermType rdfTermType = ((RDFTermTypeConstant) typeTerm).getRDFTermType();

            return computeEBV(lexicalTerm, rdfTermType, termFactory)
                    .simplify(variableNullability);
        }
        else if ((typeTerm instanceof ImmutableFunctionalTerm)
                && (((ImmutableFunctionalTerm) typeTerm).getFunctionSymbol() instanceof RDFTermTypeFunctionSymbol)) {
            ImmutableFunctionalTerm functionalTypeTerm = (ImmutableFunctionalTerm) typeTerm;
            ImmutableBiMap<DBConstant, RDFTermTypeConstant> conversionMap =
                    ((RDFTermTypeFunctionSymbol) functionalTypeTerm.getFunctionSymbol()).getConversionMap();
            ImmutableTerm magicNumberTerm = functionalTypeTerm.getTerm(0);

            return termFactory.getDisjunction(
                    Stream.concat(
                            conversionMap.entrySet().stream()
                                    .map(e -> termFactory.getConjunction(
                                        termFactory.getStrictEquality(magicNumberTerm, e.getKey()),
                                        termFactory.getImmutableExpression(this, lexicalTerm, e.getValue()))),
                            Stream.of(termFactory.getFalseOrNullFunctionalTerm(ImmutableList.of(termFactory.getDBIsNull(magicNumberTerm))))))
                    .orElseThrow(() -> new MinorOntopInternalBugException("Unexpected empty disjunction"))
                    .simplify(variableNullability);
        }
        else
            return termFactory.getImmutableFunctionalTerm(this, newTerms);
    }

    private ImmutableTerm computeEBV(ImmutableTerm lexicalTerm, RDFTermType rdfTermType, TermFactory termFactory) {
        TypeFactory typeFactory = termFactory.getTypeFactory();
        RDFDatatype xsdString = typeFactory.getXsdStringDatatype();
        RDFDatatype abstractNumeric = typeFactory.getAbstractOntopNumericDatatype();
        RDFDatatype xsdFloat = typeFactory.getXsdFloatDatatype();
        RDFDatatype xsdDouble = typeFactory.getXsdDoubleDatatype();
        RDFDatatype xsdBoolean = typeFactory.getXsdBooleanDatatype();

        if (rdfTermType.isA(xsdString)) {
            return computeStringEBV(lexicalTerm, termFactory);
        }
        else if (rdfTermType.isA(xsdFloat) || rdfTermType.isA(xsdDouble))
            return computeFloatOrDoubleEBV(lexicalTerm, rdfTermType, termFactory);
        else if (rdfTermType.isA(abstractNumeric)) {
            return computeOtherNumericEBV(lexicalTerm, rdfTermType, termFactory);
        }
        else if (rdfTermType.isA(xsdBoolean))
            return computeBooleanEBV(lexicalTerm, rdfTermType, termFactory);
        else
            return termFactory.getNullConstant();
    }

    private ImmutableTerm computeStringEBV(ImmutableTerm lexicalConstant, TermFactory termFactory) {
        return termFactory.getDBNot(termFactory.getDBIsStringEmpty(lexicalConstant));
    }

    private ImmutableExpression computeFloatOrDoubleEBV(ImmutableTerm lexicalConstant, RDFTermType rdfTermType,
                                                  TermFactory termFactory) {
        ImmutableFunctionalTerm dbNumericTerm = termFactory.getConversionFromRDFLexical2DB(lexicalConstant, rdfTermType);

        ImmutableExpression standardNumericEBVCondition = computeOtherNumericEBV(lexicalConstant, rdfTermType, termFactory);

        return termFactory.getDoubleNaN()
                .map(nan -> termFactory.getDBNonStrictNumericEquality(dbNumericTerm, nan))
                .map(notANumberCondition -> termFactory.getConjunction(
                        standardNumericEBVCondition,
                        termFactory.getDBNot(notANumberCondition)))
                .orElse(standardNumericEBVCondition);
    }

    private ImmutableExpression computeOtherNumericEBV(ImmutableTerm lexicalConstant, RDFTermType rdfTermType,
                                                 TermFactory termFactory) {
        ImmutableFunctionalTerm dbNumericTerm = termFactory.getConversionFromRDFLexical2DB(lexicalConstant, rdfTermType);

        return termFactory.getDBNot(
                termFactory.getDBNonStrictNumericEquality(dbNumericTerm, termFactory.getDBIntegerConstant(0)));
    }

    private ImmutableTerm computeBooleanEBV(ImmutableTerm lexicalConstant, RDFTermType rdfTermType, TermFactory termFactory) {
        return termFactory.getConversionFromRDFLexical2DB(lexicalConstant, rdfTermType);
    }
}
