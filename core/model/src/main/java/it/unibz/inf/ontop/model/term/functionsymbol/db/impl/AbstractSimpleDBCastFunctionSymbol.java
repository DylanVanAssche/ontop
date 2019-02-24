package it.unibz.inf.ontop.model.term.functionsymbol.db.impl;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.model.term.DBConstant;
import it.unibz.inf.ontop.model.term.ImmutableFunctionalTerm;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBTypeConversionFunctionSymbol;
import it.unibz.inf.ontop.model.type.DBTermType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public abstract class AbstractSimpleDBCastFunctionSymbol extends AbstractDBTypeConversionFunctionSymbolImpl {

    @Nullable
    private final DBTermType inputType;

    protected AbstractSimpleDBCastFunctionSymbol(@Nonnull DBTermType inputBaseType,
                                                 DBTermType targetType) {
        super(inputBaseType.isAbstract()
                ? "to" + targetType
                : inputBaseType + "To" + targetType,
                inputBaseType, targetType);
        this.inputType = inputBaseType.isAbstract() ? null : inputBaseType;
    }

    @Override
    protected DBConstant convertDBConstant(DBConstant constant, TermFactory termFactory) {
        return termFactory.getDBConstant((constant).getValue(), getTargetType());
    }

    /**
     * Tries to simplify nested casts
     */
    @Override
    protected ImmutableTerm buildTermFromFunctionalTerm(ImmutableFunctionalTerm subTerm,
                                                        TermFactory termFactory, VariableNullability variableNullability) {
        if ((inputType != null) && inputType.equals(getTargetType()))
            return subTerm;

        if (subTerm.getFunctionSymbol() instanceof DBTypeConversionFunctionSymbol) {
            DBTypeConversionFunctionSymbol functionSymbol =
                    (DBTypeConversionFunctionSymbol) subTerm.getFunctionSymbol();

            ImmutableTerm subSubTerm = subTerm.getTerm(0);

            DBTermType targetType = getTargetType();

            if (functionSymbol.isSimple()) {
                return functionSymbol.getInputType()
                        .map(input -> input.equals(targetType)
                                ? subSubTerm
                                : termFactory.getDBCastFunctionalTerm(input, targetType, subSubTerm))
                        .orElseGet(() -> termFactory.getDBCastFunctionalTerm(targetType, subSubTerm));
            }
        }
        // Default
        return super.buildTermFromFunctionalTerm(subTerm, termFactory, variableNullability);
    }

    @Override
    public Optional<DBTermType> getInputType() {
        return Optional.ofNullable(inputType);
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    protected boolean isAlwaysInjective() {
        return getInputType().isPresent();
    }

    @Override
    public boolean canBePostProcessed(ImmutableList<? extends ImmutableTerm> arguments) {
        return getInputType().isPresent();
    }
}
