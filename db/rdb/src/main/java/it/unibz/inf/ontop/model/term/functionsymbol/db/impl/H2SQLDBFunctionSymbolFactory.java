package it.unibz.inf.ontop.model.term.functionsymbol.db.impl;

import com.google.common.collect.*;
import com.google.inject.Inject;
import it.unibz.inf.ontop.dbschema.DatabaseInfoSupplier;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBConcatFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBTypeConversionFunctionSymbol;
import it.unibz.inf.ontop.model.type.DBTermType;
import it.unibz.inf.ontop.model.type.DBTypeFactory;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.vocabulary.XSD;

import java.util.Optional;
import java.util.function.Function;

import static it.unibz.inf.ontop.model.type.impl.DefaultSQLDBTypeFactory.VARBINARY_STR;

public class H2SQLDBFunctionSymbolFactory extends AbstractSQLDBFunctionSymbolFactory {

    private static final String UUID_STR = "RANDOM_UUID";
    private static final String REGEXP_LIKE_STR = "REGEXP_LIKE";
    private static final String LISTAGG_STR = "LISTAGG";

    private static final String UNSUPPORTED_MSG = "Not supported by H2";
    private final DatabaseInfoSupplier databaseInfoSupplier;

    @Inject
    private H2SQLDBFunctionSymbolFactory(TypeFactory typeFactory, DatabaseInfoSupplier databaseInfoSupplier) {
        super(createH2RegularFunctionTable(typeFactory), typeFactory);
        this.databaseInfoSupplier = databaseInfoSupplier;
    }

    protected static ImmutableTable<String, Integer, DBFunctionSymbol> createH2RegularFunctionTable(
            TypeFactory typeFactory) {

        Table<String, Integer, DBFunctionSymbol> table = HashBasedTable.create(
                createDefaultRegularFunctionTable(typeFactory));

        return ImmutableTable.copyOf(table);
    }

    @Override
    protected ImmutableMap<DBTermType, DBTypeConversionFunctionSymbol> createNormalizationMap() {
        DBTypeFactory dbTypeFactory = typeFactory.getDBTypeFactory();
        ImmutableMap.Builder<DBTermType, DBTypeConversionFunctionSymbol> builder = ImmutableMap.builder();
        builder.putAll(super.createNormalizationMap());

        DBTermType varBinary = dbTypeFactory.getDBTermType(VARBINARY_STR);
        builder.put(varBinary, createHexBinaryNormFunctionSymbol(varBinary));

        return builder.build();
    }

    /**
     * Ensure geometries are recast back from text.
     *
     * At moment only WKT is supported, but in the future GML could also be.
     * It is therefore important to consider the target datatype for choosing the right normalization.
     *
     */
    @Override
    protected ImmutableTable<DBTermType, RDFDatatype, DBTypeConversionFunctionSymbol> createNormalizationTable() {
        DBTypeFactory dbTypeFactory = typeFactory.getDBTypeFactory();
        ImmutableTable.Builder<DBTermType, RDFDatatype, DBTypeConversionFunctionSymbol> builder = ImmutableTable.builder();

        //GEOMETRY
        DBTermType defaultDBGeometryType = dbTypeFactory.getDBGeometryType();
        DBTypeConversionFunctionSymbol geometryNormFunctionSymbol = createGeometryNormFunctionSymbol(defaultDBGeometryType);
        // For WKT
        builder.put(defaultDBGeometryType,typeFactory.getWktLiteralDatatype(), geometryNormFunctionSymbol);

        return builder.build();
    }

    @Override
    protected String serializeContains(ImmutableList<? extends ImmutableTerm> terms,
                                       Function<ImmutableTerm, String> termConverter,
                                       TermFactory termFactory) {
        return String.format("(POSITION(%s,%s) > 0)",
                termConverter.apply(terms.get(1)),
                termConverter.apply(terms.get(0)));
    }

    @Override
    protected String serializeStrBefore(ImmutableList<? extends ImmutableTerm> terms,
                                        Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        String str = termConverter.apply(terms.get(0));
        String before = termConverter.apply(terms.get(1));

        return String.format("LEFT(%s,POSITION(%s,%s)-1)", str, before, str);
    }

    @Override
    protected String serializeStrAfter(ImmutableList<? extends ImmutableTerm> terms,
                                       Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        String str = termConverter.apply(terms.get(0));
        String after = termConverter.apply(terms.get(1));

        // sign return 1 if positive number, 0 if 0, and -1 if negative number
        // it will return everything after the value if it is present or it will return an empty string if it is not present
        return String.format("SUBSTRING(%s,POSITION(%s,%s) + LENGTH(%s), SIGN(POSITION(%s,%s)) * LENGTH(%s))",
                str, after, str, after, after, str, str);
    }

    @Override
    protected String serializeMD5(ImmutableList<? extends ImmutableTerm> terms,
                                  Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        // TODO: throw a better exception
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected String serializeSHA1(ImmutableList<? extends ImmutableTerm> terms,
                                   Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        // TODO: throw a better exception
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected String serializeSHA256(ImmutableList<? extends ImmutableTerm> terms,
                                     Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        return String.format("HASH('SHA256', STRINGTOUTF8(%s), 1)", termConverter.apply(terms.get(0)));
    }

    @Override
    protected String serializeSHA512(ImmutableList<? extends ImmutableTerm> terms,
                                     Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        // TODO: throw a better exception
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected String serializeTz(ImmutableList<? extends ImmutableTerm> terms,
                                 Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        // TODO: throw a better exception
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected String serializeDBRowNumber(Function<ImmutableTerm, String> converter, TermFactory termFactory) {
        return "ROWNUM()";
    }

    @Override
    protected DBConcatFunctionSymbol createNullRejectingDBConcat(int arity) {
        return new NullRejectingDBConcatFunctionSymbol(CONCAT_OP_STR, arity, dbStringType, abstractRootDBType, true);
    }

    @Override
    protected DBConcatFunctionSymbol createDBConcatOperator(int arity) {
        return getNullRejectingDBConcat(arity);
    }

    /**
     * Treats NULLs as empty strings
     */
    @Override
    protected DBConcatFunctionSymbol createRegularDBConcat(int arity) {
        return new NullToleratingDBConcatFunctionSymbol(CONCAT_STR, arity, dbStringType, abstractRootDBType, false);
    }

    @Override
    protected Optional<DBFunctionSymbol> createCeilFunctionSymbol(DBTermType dbTermType) {
        return Optional.of(new UnaryDBFunctionSymbolWithSerializerImpl(CEIL_STR, dbTermType, dbTermType, false,
                (terms, termConverter, termFactory) -> String.format(
                        "CAST(CEIL(%s) AS %s)", termConverter.apply(terms.get(0)), dbTermType.getCastName())));
    }

    @Override
    protected Optional<DBFunctionSymbol> createFloorFunctionSymbol(DBTermType dbTermType) {
        // TODO: check term type
        return Optional.of(new UnaryDBFunctionSymbolWithSerializerImpl(FLOOR_STR, dbTermType, dbTermType, false,
                (terms, termConverter, termFactory) -> String.format(
                        "CAST(FLOOR(%s) AS %s)", termConverter.apply(terms.get(0)), dbTermType.getCastName())));
    }

    @Override
    protected Optional<DBFunctionSymbol> createRoundFunctionSymbol(DBTermType dbTermType) {
        // TODO: check term type
        return Optional.of(new UnaryDBFunctionSymbolWithSerializerImpl(ROUND_STR, dbTermType, dbTermType, false,
                (terms, termConverter, termFactory) -> String.format(
                        "CAST(ROUND(%s) AS %s)", termConverter.apply(terms.get(0)), dbTermType.getCastName())));
    }

    /**
     * Asks the timezone to be included
     */
    @Override
    protected String serializeDateTimeNorm(ImmutableList<? extends ImmutableTerm> terms,
                                           Function<ImmutableTerm, String> termConverter, TermFactory termFactory) {
        return String.format("REPLACE(FORMATDATETIME(%s,'yyyy-MM-dd HH:mm:ss.SSSXXX'), ' ', 'T')", termConverter.apply(terms.get(0)));
    }

    @Override
    protected DBFunctionSymbol createDBAvg(DBTermType inputType, boolean isDistinct) {
        // To make sure the AVG does not return an integer but a decimal
        if (inputType.equals(dbIntegerType))
            return new ForcingFloatingDBAvgFunctionSymbolImpl(inputType, dbDecimalType, isDistinct);

        return super.createDBAvg(inputType, isDistinct);
    }

    @Override
    protected String getUUIDNameInDialect() {
        return UUID_STR;
    }


}
