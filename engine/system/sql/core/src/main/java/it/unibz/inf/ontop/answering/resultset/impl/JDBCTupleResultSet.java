package it.unibz.inf.ontop.answering.resultset.impl;

import com.google.common.collect.*;
import it.unibz.inf.ontop.answering.resultset.TupleResultSet;
import it.unibz.inf.ontop.exception.OntopConnectionException;
import it.unibz.inf.ontop.iq.node.ConstructionNode;
import it.unibz.inf.ontop.model.atom.DistinctVariableOnlyDataAtom;
import it.unibz.inf.ontop.model.term.DBConstant;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.type.DBTermType;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

public class JDBCTupleResultSet extends AbstractTupleResultSet implements TupleResultSet {

    private final ImmutableSortedSet<Variable> sqlSignature;
    private final ImmutableMap<Variable, DBTermType> sqlTypeMap;
    private final ImmutableSubstitution<ImmutableTerm> sparqlVar2Term;
    private final SubstitutionFactory substitutionFactory;
    private final TermFactory termFactory;

    public JDBCTupleResultSet(ResultSet rs,
                              ImmutableSortedSet<Variable> sqlSignature,
                              ImmutableMap<Variable, DBTermType> sqlTypeMap,
                              ConstructionNode constructionNode,
                              DistinctVariableOnlyDataAtom answerAtom,
                              TermFactory termFactory,
                              SubstitutionFactory substitutionFactory) {
        super(rs, ImmutableSortedSet.copyOf(answerAtom.getArguments()));
        this.sqlSignature = sqlSignature;
        this.sqlTypeMap = sqlTypeMap;
        this.substitutionFactory = substitutionFactory;
        this.termFactory = termFactory;
        this.sparqlVar2Term = constructionNode.getSubstitution();
    }


    @Override
    protected SQLOntopBindingSet readCurrentRow() throws OntopConnectionException {

        //builder (+loop) in order to throw checked exception
        final ImmutableMap.Builder<Variable,DBConstant> builder = ImmutableMap.builder();
        Iterator<Variable> it = sqlSignature.iterator();
        try {
            for (int i = 1; i <= getColumnCount(); i++) {
                Variable var = it.next();
                builder.put(
                        var,
                        termFactory.getDBConstant(
                            rs.getString(i),
                            sqlTypeMap.get(var)
                        ));
            }
        } catch (SQLException e) {
            throw new OntopConnectionException(e);
        }
        return new SQLOntopBindingSet(
                signature,
                substitutionFactory.getSubstitution(builder.build()),
                sparqlVar2Term
        );
    }
}
