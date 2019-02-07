package it.unibz.inf.ontop.answering.reformulation.generation.serializer;

import com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.dbschema.QualifiedAttributeID;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.Variable;

@FunctionalInterface
public interface SQLTermSerializer {

    String serialize(ImmutableTerm term, ImmutableMap<Variable, QualifiedAttributeID> var2ColumnMap)
            throws SQLSerializationException;
}
