package it.unibz.inf.ontop.model.term.functionsymbol.db.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.type.DBTermType;

public abstract class AbstractDBIsNullOrNotFunctionSymbol extends DBBooleanFunctionSymbolImpl {

    private static final String IS_NULL_NAME = "IS_NULL";
    private static final String IS_NOT_NULL_NAME = "IS_NOT_NULL";

    private final boolean isNull;

    protected AbstractDBIsNullOrNotFunctionSymbol(boolean isNull, DBTermType dbBooleanTermType, DBTermType rootDBTermType) {
        super(isNull ? IS_NULL_NAME : IS_NOT_NULL_NAME, ImmutableList.of(rootDBTermType), dbBooleanTermType);
        this.isNull = isNull;
    }

    @Override
    public boolean blocksNegation() {
        return false;
    }

    @Override
    public ImmutableExpression negate(ImmutableList<? extends ImmutableTerm> subTerms, TermFactory termFactory) {
        ImmutableTerm subTerm = subTerms.get(0);
        return isNull
                ? termFactory.getDBIsNotNull(subTerm)
                : termFactory.getDBIsNull(subTerm);
    }

    @Override
    protected boolean isAlwaysInjective() {
        return false;
    }

    @Override
    public boolean canBePostProcessed(ImmutableList<? extends ImmutableTerm> arguments) {
        return true;
    }

    @Override
    protected ImmutableTerm buildTermAfterEvaluation(ImmutableList<ImmutableTerm> newTerms,
                                                     TermFactory termFactory, VariableNullability variableNullability) {
        ImmutableTerm newTerm = newTerms.get(0);

        EvaluationResult evaluationResult = newTerm.evaluateIsNotNull(variableNullability);
        switch (evaluationResult.getStatus()) {
            case SAME_EXPRESSION:
                return termFactory.getImmutableExpression(this, newTerm);
            case SIMPLIFIED_EXPRESSION:
                return evaluationResult.getSimplifiedExpression()
                        .map(e -> isNull ? e.negate(termFactory) : e)
                        .orElseThrow(() -> new MinorOntopInternalBugException("A simplified expression was expected"));
            case IS_NULL:
                return termFactory.getNullConstant();
            case IS_FALSE:
                return termFactory.getDBBooleanConstant(isNull);
            case IS_TRUE:
                return termFactory.getDBBooleanConstant(!isNull);
            default:
                throw new MinorOntopInternalBugException("Unexpected status");
        }
    }

    @Override
    public boolean isNullable(ImmutableSet<Integer> nullableIndexes) {
        return false;
    }

    @Override
    protected boolean tolerateNulls() {
        return true;
    }
}
