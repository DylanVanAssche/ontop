package it.unibz.inf.ontop.datalog.impl;

import com.google.inject.Inject;
import fj.F;
import fj.F2;
import fj.data.List;
import it.unibz.inf.ontop.datalog.DatalogFactory;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.DatatypePredicate;
import it.unibz.inf.ontop.model.term.functionsymbol.ExpressionOperation;
import it.unibz.inf.ontop.model.term.functionsymbol.OperationPredicate;
import it.unibz.inf.ontop.model.term.functionsymbol.Predicate;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.vocabulary.XSD;


import java.util.ArrayList;


/**
 * Tool methods when manipulate some Datalog programs and their rules.
 */
public class DatalogTools {
    private final TermFactory termFactory;
    private final DatalogFactory datalogFactory;

    private final Expression TRUE_EQ;

    private final  F<Function, Boolean> IS_DATA_OR_LJ_OR_JOIN_ATOM_FCT;
    private final  F<Function, Boolean> IS_NOT_DATA_OR_COMPOSITE_ATOM_FCT;
    private final  F<Function, Boolean> IS_BOOLEAN_ATOM_FCT;

    @Inject
    private DatalogTools(TermFactory termFactory, DatalogFactory datalogFactory) {
        this.termFactory = termFactory;
        this.datalogFactory = datalogFactory;
        ValueConstant valueTrue = termFactory.getBooleanConstant(true);
        TRUE_EQ = termFactory.getFunctionEQ(valueTrue, valueTrue);
        IS_DATA_OR_LJ_OR_JOIN_ATOM_FCT = this::isDataOrLeftJoinOrJoinAtom;
        IS_NOT_DATA_OR_COMPOSITE_ATOM_FCT = atom -> !isDataOrLeftJoinOrJoinAtom(atom);
        IS_BOOLEAN_ATOM_FCT = atom -> atom.isOperation() || isXsdBoolean(atom.getFunctionSymbol());
    }

    public Boolean isDataOrLeftJoinOrJoinAtom(Function atom) {
        return atom.isDataFunction() || isLeftJoinOrJoinAtom(atom);
    }

    public Boolean isLeftJoinOrJoinAtom(Function atom) {
        Predicate predicate = atom.getFunctionSymbol();
        return predicate.equals(datalogFactory.getSparqlLeftJoinPredicate()) ||
                predicate.equals(datalogFactory.getSparqlJoinPredicate());
    }

    public List<Function> filterDataAndCompositeAtoms(List<Function> atoms) {
        return atoms.filter(IS_DATA_OR_LJ_OR_JOIN_ATOM_FCT);
    }

    public List<Function> filterNonDataAndCompositeAtoms(List<Function> atoms) {
        return atoms.filter(IS_NOT_DATA_OR_COMPOSITE_ATOM_FCT);
    }

    public List<Function> filterBooleanAtoms(List<Function> atoms) {
        return atoms.filter(IS_BOOLEAN_ATOM_FCT);
    }

    /**
     * Folds a list of boolean atoms into one AND(AND(...)) boolean atom.
     */
    public Expression foldBooleanConditions(List<Function> booleanAtoms) {
        if (booleanAtoms.length() == 0)
            return TRUE_EQ;

        Expression firstBooleanAtom = convertOrCastIntoBooleanAtom( booleanAtoms.head());

        return booleanAtoms.tail().foldLeft(new F2<Expression, Function, Expression>() {
            @Override
            public Expression f(Expression previousAtom, Function currentAtom) {
                return termFactory.getFunctionAND(previousAtom, currentAtom);
            }
        }, firstBooleanAtom);
    }

    private Expression convertOrCastIntoBooleanAtom(Function atom) {
        if (atom instanceof Expression)
            return (Expression) atom;

        Predicate predicate = atom.getFunctionSymbol();
        if (predicate instanceof OperationPredicate)
            return termFactory.getExpression((OperationPredicate)predicate,
                    atom.getTerms());
        // XSD:BOOLEAN case
        if ((predicate instanceof DatatypePredicate)
                && ((DatatypePredicate) predicate).getReturnedType().isA(XSD.BOOLEAN)) {
            return termFactory.getExpression(ExpressionOperation.IS_TRUE, atom);
        }

        throw new IllegalArgumentException(atom + " is not a boolean atom");
    }

    public Expression foldBooleanConditions(java.util.List<Function> booleanAtoms) {
        return foldBooleanConditions(List.iterableList(booleanAtoms));
    }

    private static boolean isXsdBoolean(Predicate predicate) {
        return (predicate instanceof DatatypePredicate)
                && ((DatatypePredicate) predicate).getReturnedType().isA(XSD.BOOLEAN);
    }
}
