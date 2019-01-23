package it.unibz.inf.ontop.model.term.functionsymbol.impl;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.model.term.ImmutableExpression;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.type.DBTermType;
import it.unibz.inf.ontop.model.type.MetaRDFTermType;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.RDFTermType;


public class LexicalNonStrictEqualityFunctionSymbolImpl extends AbstractLexicalNonStrictEqOrInequalityFunctionSymbol {

    protected LexicalNonStrictEqualityFunctionSymbolImpl(MetaRDFTermType metaRDFTermType, RDFDatatype xsdBooleanType,
                                                         RDFDatatype xsdDateTimeType, RDFDatatype xsdStringType, DBTermType dbStringType,
                                                         DBTermType dbBooleanType) {
        super("LEX_NON_STRICT_EQ", metaRDFTermType, xsdBooleanType, xsdDateTimeType, xsdStringType, dbStringType, dbBooleanType);
    }

    @Override
    protected ImmutableTerm computeBooleanEqualityOrInequality(ImmutableTerm dbTerm1, ImmutableTerm dbTerm2,
                                                               TermFactory termFactory, VariableNullability variableNullability) {
        return termFactory.getDBNonStrictBooleanEquality(dbTerm1, dbTerm2)
                .simplify(variableNullability);
    }

    @Override
    protected ImmutableTerm computeStringEqualityOrInequality(ImmutableTerm dbTerm1, ImmutableTerm dbTerm2, TermFactory termFactory,
                                                              VariableNullability variableNullability) {
        return termFactory.getDBNonStrictStringEquality(dbTerm1, dbTerm2)
                .simplify(variableNullability);
    }

    @Override
    protected ImmutableTerm computeDatetimeEqualityOrInequality(ImmutableTerm dbTerm1, ImmutableTerm dbTerm2,
                                                                TermFactory termFactory,
                                                                VariableNullability variableNullability) {
        return termFactory.getDBNonStrictDatetimeEquality(dbTerm1, dbTerm2)
                .simplify(variableNullability);
    }

    @Override
    protected ImmutableTerm computeDefaultSameTypeEqualityOrInequality(RDFTermType termType, ImmutableTerm dbTerm1,
                                                                       ImmutableTerm dbTerm2, TermFactory termFactory,
                                                                       VariableNullability variableNullability) {
        ImmutableExpression equality = termFactory.getStrictEquality(dbTerm1, dbTerm2);

        return (termType instanceof RDFDatatype)
                ? termFactory.getTrueOrNullFunctionalTerm(ImmutableList.of(equality))
                .simplify(variableNullability)
                : equality.simplify(variableNullability);
    }

    @Override
    protected ImmutableTerm computeDefaultDifferentTypeEqualityOrInequality(RDFTermType termType1, RDFTermType termType2,
                                                                            TermFactory termFactory) {
        return ((termType1 instanceof RDFDatatype) && termType2 instanceof RDFDatatype)
                ? termFactory.getNullConstant()
                : termFactory.getDBBooleanConstant(false);
    }
}
