package it.unibz.inf.ontop.model.type.impl;

import com.google.common.collect.ImmutableMap;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import it.unibz.inf.ontop.model.type.*;
import it.unibz.inf.ontop.model.vocabulary.XSD;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * See https://www.w3.org/TR/r2rml/#natural-mapping
 */
public class DefaultSQLDBTypeFactory implements SQLDBTypeFactory {

    protected static final String ABSTRACT_DB_TYPE_STR = "AbstractDBType";

    protected static final String TEXT_STR = "TEXT";
    protected static final String CHAR_STR = "CHAR";
    protected static final String CHARACTER_STR = "CHARACTER";
    protected static final String VARCHAR_STR = "VARCHAR";
    protected static final String CHAR_VAR_STR = "CHARACTER VARYING";
    protected static final String CLOB_STR = "CLOB";
    protected static final String CHAR_LARGE_STR = "CHARACTER LARGE OBJECT";
    protected static final String NATIONAL_CHAR_STR = "NATIONAL CHARACTER";
    protected static final String NATIONAL_CHAR_VAR_STR = "NATIONAL CHARACTER VARYING";
    protected static final String NATIONAL_CHAR_LARGE_STR = "NATIONAL CHARACTER LARGE OBJECT";
    protected static final String INTEGER_STR = "INTEGER";
    protected static final String SMALLINT_STR = "SMALLINT";
    protected static final String BIGINT_STR = "BIGINT";
    protected static final String NUMERIC_STR = "NUMERIC";
    protected static final String DECIMAL_STR = "DECIMAL";
    protected static final String FLOAT_STR = "FLOAT";
    protected static final String REAL_STR = "REAL";
    protected static final String DOUBLE_STR = "DOUBLE";
    protected static final String DOUBLE_PREC_STR = "DOUBLE PRECISION";
    protected static final String BOOLEAN_STR = "BOOLEAN";
    protected static final String DATE_STR = "DATE";
    protected static final String TIME_STR = "TIME";
    protected static final String TIMESTAMP_STR = "TIMESTAMP";
    protected static final String BINARY_STR = "BINARY";
    protected static final String BINARY_VAR_STR = "BINARY VARYING";
    protected static final String BINARY_LARGE_STR = "BINARY LARGE OBJECT";

    protected enum DefaultTypeCode {
        STRING,
        HEXBINARY,
        LARGE_INTEGER,
        DECIMAL,
        DOUBLE,
        BOOLEAN,
        DATE,
        TIME,
        DATETIMESTAMP
    }

    // MUTABLE
    private final Map<String, DBTermType> sqlTypeMap;
    private final ImmutableMap<DefaultTypeCode, String> defaultTypeCodeMap;

    @AssistedInject
    private DefaultSQLDBTypeFactory(@Assisted TermType rootTermType, @Assisted TypeFactory typeFactory) {
        this(createDefaultSQLTypeMap(rootTermType, typeFactory), ImmutableMap.copyOf(createDefaultSQLCodeMap()));
    }

    protected DefaultSQLDBTypeFactory(Map<String, DBTermType> typeMap,
                                      ImmutableMap<DefaultTypeCode, String> defaultTypeCodeMap) {
        sqlTypeMap = typeMap;
        this.defaultTypeCodeMap = defaultTypeCodeMap;
    }

    /**
     * Returns a mutable map so that it can be modified by sub-classes
     */
    protected static Map<String, DBTermType> createDefaultSQLTypeMap(TermType rootTermType, TypeFactory typeFactory) {
        DBTermType rootDBType = new NonStringNonNumberNonBooleanDBTermType(ABSTRACT_DB_TYPE_STR, rootTermType.getAncestry(), true);

        TermTypeAncestry rootAncestry = rootDBType.getAncestry();

        RDFDatatype xsdString = typeFactory.getXsdStringDatatype();
        RDFDatatype hexBinary = typeFactory.getDatatype(XSD.HEXBINARY);
        RDFDatatype xsdInteger = typeFactory.getXsdIntegerDatatype();
        RDFDatatype xsdDecimal = typeFactory.getXsdDecimalDatatype();
        RDFDatatype xsdDouble = typeFactory.getXsdDoubleDatatype();

        // TODO: complete
        return Stream.of(rootDBType,
                    new StringDBTermType(TEXT_STR, rootAncestry, xsdString),
                    new StringDBTermType(CHAR_STR, rootAncestry, xsdString),
                    // TODO: group aliases?
                    new StringDBTermType(CHARACTER_STR, rootAncestry, xsdString),
                    new StringDBTermType(VARCHAR_STR, rootAncestry, xsdString),
                    new StringDBTermType(CHAR_VAR_STR, rootAncestry, xsdString),
                    new StringDBTermType(CHAR_LARGE_STR, rootAncestry, xsdString),
                    new StringDBTermType(CLOB_STR, rootAncestry, xsdString),
                    new StringDBTermType(NATIONAL_CHAR_STR, rootAncestry, xsdString),
                    new StringDBTermType(NATIONAL_CHAR_VAR_STR, rootAncestry, xsdString),
                    new StringDBTermType(NATIONAL_CHAR_LARGE_STR, rootAncestry, xsdString),
                    new NonStringNonNumberNonBooleanDBTermType(BINARY_STR, rootAncestry, hexBinary),
                    new NonStringNonNumberNonBooleanDBTermType(BINARY_VAR_STR, rootAncestry, hexBinary),
                    new NonStringNonNumberNonBooleanDBTermType(BINARY_LARGE_STR, rootAncestry, hexBinary),
                    new NumberDBTermType(INTEGER_STR, rootAncestry, xsdInteger),
                    new NumberDBTermType(SMALLINT_STR, rootAncestry, xsdInteger),
                    new NumberDBTermType(BIGINT_STR, rootAncestry, xsdInteger),
                    new NumberDBTermType(NUMERIC_STR, rootAncestry, xsdDecimal),
                    new NumberDBTermType(DECIMAL_STR, rootAncestry, xsdDecimal),
                    new NumberDBTermType(FLOAT_STR, rootTermType.getAncestry(), xsdDouble),
                    new NumberDBTermType(REAL_STR, rootTermType.getAncestry(), xsdDouble),
                    new NumberDBTermType(DOUBLE_STR, rootTermType.getAncestry(), xsdDouble),
                    new NumberDBTermType(DOUBLE_PREC_STR, rootTermType.getAncestry(), xsdDouble),
                    new BooleanDBTermType(BOOLEAN_STR, rootTermType.getAncestry(), typeFactory.getXsdBooleanDatatype()),
                    new NonStringNonNumberNonBooleanDBTermType(DATE_STR, rootAncestry, typeFactory.getDatatype(XSD.DATE)),
                    new NonStringNonNumberNonBooleanDBTermType(TIME_STR, rootTermType.getAncestry(), typeFactory.getDatatype(XSD.TIME)),
                    new NonStringNonNumberNonBooleanDBTermType(TIMESTAMP_STR, rootTermType.getAncestry(), typeFactory.getXsdDatetimeDatatype()))
                .collect(Collectors.toMap(
                        DBTermType::getName,
                        t -> t));
    }

    /**
     * Returns a mutable map so that it can be modified by sub-classes
     *
     * NB: we use the largest option among the DB datatypes mapped to the same XSD type.
     *
     */
    protected static Map<DefaultTypeCode, String> createDefaultSQLCodeMap() {
        Map<DefaultTypeCode, String> map = new HashMap<>();
        map.put(DefaultTypeCode.STRING, TEXT_STR);
        map.put(DefaultTypeCode.HEXBINARY, BINARY_LARGE_STR);
        map.put(DefaultTypeCode.LARGE_INTEGER, BIGINT_STR);
        map.put(DefaultTypeCode.DECIMAL, DECIMAL_STR);
        map.put(DefaultTypeCode.DOUBLE, DOUBLE_STR);
        map.put(DefaultTypeCode.BOOLEAN, BOOLEAN_STR);
        map.put(DefaultTypeCode.DATE, DATE_STR);
        map.put(DefaultTypeCode.TIME, TIME_STR);
        map.put(DefaultTypeCode.DATETIMESTAMP, TIMESTAMP_STR);
        return map;
    }

    @Override
    public DBTermType getDBTermType(int typeCode, String typeName) {
        String typeString = preprocessTypeName(typeName);

        /*
         * Creates a new term type if not known
         */
        return sqlTypeMap.computeIfAbsent(typeString,
                s -> new NonStringNonNumberNonBooleanDBTermType(s, sqlTypeMap.get(ABSTRACT_DB_TYPE_STR).getAncestry(), false));
    }

    @Override
    public String getDBTrueLexicalValue() {
        return "TRUE";
    }

    @Override
    public String getDBFalseLexicalValue() {
        return "FALSE";
    }

    @Override
    public String getNullLexicalValue() {
        return "NULL";
    }

    /**
     * Can be overridden
     */
    protected String preprocessTypeName(String typeName) {
        return typeName.replaceAll("\\([\\d, ]+\\)", "");
    }

    @Override
    public DBTermType getDBStringType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.STRING));
    }

    @Override
    public DBTermType getDBLargeIntegerType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.LARGE_INTEGER));
    }

    @Override
    public DBTermType getDBDecimalType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.DECIMAL));
    }

    @Override
    public DBTermType getDBBooleanType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.BOOLEAN));
    }

    @Override
    public DBTermType getDBDateType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.DATE));
    }

    @Override
    public DBTermType getDBTimeType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.TIME));
    }

    @Override
    public DBTermType getDBDateTimestampType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.DATETIMESTAMP));
    }

    @Override
    public DBTermType getDBDoubleType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.DOUBLE));
    }

    @Override
    public DBTermType getDBHexBinaryType() {
        return sqlTypeMap.get(defaultTypeCodeMap.get(DefaultTypeCode.HEXBINARY));
    }

    @Override
    public DBTermType getAbstractRootDBType() {
        return sqlTypeMap.get(ABSTRACT_DB_TYPE_STR);
    }
}
