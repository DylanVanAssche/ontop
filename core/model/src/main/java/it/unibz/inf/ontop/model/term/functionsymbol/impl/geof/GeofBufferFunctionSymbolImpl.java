package it.unibz.inf.ontop.model.term.functionsymbol.impl.geof;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.FunctionSymbolFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBFunctionSymbolFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBMathBinaryOperator;
import it.unibz.inf.ontop.model.type.DBTypeFactory;
import it.unibz.inf.ontop.model.type.ObjectRDFType;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.vocabulary.UOM;
import org.apache.commons.rdf.api.IRI;
//import org.apache.sis.referencing.CRS;
//import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.annotation.Nonnull;

import static it.unibz.inf.ontop.model.term.functionsymbol.impl.geof.DistanceUnit.*;
import static java.lang.Math.PI;

public class GeofBufferFunctionSymbolImpl extends AbstractGeofWKTFunctionSymbolImpl {

    public GeofBufferFunctionSymbolImpl(@Nonnull IRI functionIRI, RDFDatatype wktLiteralType, RDFDatatype decimalType, ObjectRDFType iriType) {
        super("GEOF_BUFFER", functionIRI, ImmutableList.of(wktLiteralType, decimalType, iriType), wktLiteralType);
    }

    /**
     * @param subLexicalTerms (geom, dist, unit)
     */
    @Override
    protected ImmutableTerm computeDBTerm(ImmutableList<ImmutableTerm> subLexicalTerms, ImmutableList<ImmutableTerm> typeTerms, TermFactory termFactory) {

        ImmutableTerm term = subLexicalTerms.get(0);
        WKTLiteralValue wktLiteralValue = GeoUtils.extractWKTLiteralValue(termFactory, term);

        DistanceUnit inputUnit = GeoUtils.getUnitFromSRID(wktLiteralValue.getSRID().getIRIString());// DistanceUnit.fromIRI(srid0);
        ImmutableTerm distance = subLexicalTerms.get(1);
        DistanceUnit distanceUnit = DistanceUnit.findByIRI(((DBConstant) subLexicalTerms.get(2)).getValue());

        DBFunctionSymbolFactory dbFunctionSymbolFactory = termFactory.getDBFunctionSymbolFactory();
        DBTypeFactory dbTypeFactory = termFactory.getTypeFactory().getDBTypeFactory();
        DBMathBinaryOperator times = dbFunctionSymbolFactory.getDBMathBinaryOperator("*", dbTypeFactory.getDBDoubleType());

        ImmutableTerm geom = wktLiteralValue.getGeometry();

        // ST_AsTexT(ST_BUFFER(geom, distance))
        if (inputUnit == DEGREE && distanceUnit == METRE) {
            final double EARTH_MEAN_RADIUS_METER = 6370986;
            final double ratio = 180 / PI / EARTH_MEAN_RADIUS_METER;
            DBConstant ratioConstant = termFactory.getDBConstant(String.valueOf(ratio), dbTypeFactory.getDBDoubleType());
            ImmutableFunctionalTerm distanceInDegree = termFactory.getImmutableFunctionalTerm(times, distance, ratioConstant);
            // TODO termFactory.getDBAsText might be redundant. Similarly in other cases
            return termFactory.getDBAsText(termFactory.getDBBuffer(geom, distanceInDegree)).simplify();
        } else if (inputUnit == DEGREE && distanceUnit == DEGREE) {
            // ST_BUFFER
            return  termFactory.getDBAsText(termFactory.getDBBuffer(geom, distance)).simplify();
        } else if (inputUnit == DEGREE && distanceUnit == RADIAN) {
            final double ratio = 180 / PI;
            DBConstant ratioConstant = termFactory.getDBConstant(String.valueOf(ratio), dbTypeFactory.getDBDoubleType());
            ImmutableFunctionalTerm distanceInDegree = termFactory.getImmutableFunctionalTerm(times, distance, ratioConstant);
            return termFactory.getDBAsText(termFactory.getDBBuffer(geom, distanceInDegree)).simplify();
        } else if (inputUnit == METRE && distanceUnit == METRE) {
            // ST_BUFFER
            return termFactory.getDBAsText(termFactory.getDBBuffer(geom, distance)).simplify();
        } else {
            throw new IllegalArgumentException(
                    String.format("Unsupported unit combination for geof:buffer. inputUnit=%s, outputUnit=%s ",
                            inputUnit, distanceUnit));
        }

    }

}
