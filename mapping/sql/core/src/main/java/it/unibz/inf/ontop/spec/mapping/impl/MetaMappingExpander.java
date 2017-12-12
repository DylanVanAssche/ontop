package it.unibz.inf.ontop.spec.mapping.impl;

/*
 * #%L
 * ontop-obdalib-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.dbschema.*;
import it.unibz.inf.ontop.exception.MetaMappingExpansionException;
import it.unibz.inf.ontop.injection.OntopMappingSQLSettings;
import it.unibz.inf.ontop.spec.mapping.OBDASQLQuery;
import it.unibz.inf.ontop.spec.mapping.SQLMappingFactory;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPTriplesMap;
import it.unibz.inf.ontop.model.*;
import it.unibz.inf.ontop.spec.mapping.pp.impl.OntopNativeSQLPPTriplesMap;
import it.unibz.inf.ontop.model.term.functionsymbol.URITemplatePredicate;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.spec.mapping.parser.impl.SelectQueryAttributeExtractor2;
import it.unibz.inf.ontop.spec.mapping.parser.exception.InvalidSelectQueryException;
import it.unibz.inf.ontop.spec.mapping.parser.exception.UnsupportedSelectQueryException;
import it.unibz.inf.ontop.utils.IDGenerator;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.LocalJDBCConnectionUtils;
import it.unibz.inf.ontop.utils.URITemplates;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

import static it.unibz.inf.ontop.model.OntopModelSingletons.TERM_FACTORY;


/**
 * 
 * @author xiao
 *
 */
public class MetaMappingExpander {

	private static final Logger log = LoggerFactory.getLogger(MetaMappingExpander.class);
	
	private static final SQLMappingFactory MAPPING_FACTORY = SQLMappingFactoryImpl.getInstance();


	/**
	 * this method expand the input mappings, which may include meta mappings, to the concrete mappings
	 *
	 * @param mappings
	 * 		a list of mappings, which may include meta mappings
	 * @return
	 * 		expanded normal mappings
	 */
	public static ImmutableList<SQLPPTriplesMap> expand(Collection<SQLPPTriplesMap> mappings,
														OntopMappingSQLSettings settings, DBMetadata metadata)
			throws MetaMappingExpansionException {

		List<String> errorMessages = new LinkedList<>();

		List<SQLPPTriplesMap> expandedMappings = new LinkedList<>();

		for (SQLPPTriplesMap mapping : mappings) {

			boolean split = mapping.getTargetAtoms().stream()
					.anyMatch(atom -> atom.getFunctionSymbol().isTriplePredicate());

			if (split) {
				String id = mapping.getId();
				OBDASQLQuery sourceQuery = mapping.getSourceQuery();

				try (Connection connection = LocalJDBCConnectionUtils.createConnection(settings)) {

					for (ImmutableFunctionalTerm atom : mapping.getTargetAtoms()) {
						if (!atom.getFunctionSymbol().isTriplePredicate()) {
							// for normal mappings, we do not need to expand it.
							SQLPPTriplesMap newMapping = mapping.extractPPMappingAssertion(atom);

							expandedMappings.add(newMapping);
						} else {
							try {
								expandedMappings.addAll(instantiateMapping(connection, metadata, id, atom, sourceQuery.toString()));
							} catch (Exception e) {
								log.warn("Parse exception, check no SQL reserved keywords have been used " + e.getMessage());
								errorMessages.add(e.getMessage());
							}
						}
					}
				} catch (SQLException e) {
					throw new MetaMappingExpansionException(e);
				}
			}
			else
				expandedMappings.add(mapping);
		}

		if (!errorMessages.isEmpty())
			throw new MetaMappingExpansionException(Joiner.on("\n").join(errorMessages));

		return ImmutableList.copyOf(expandedMappings);
	}

	private static List<SQLPPTriplesMap> instantiateMapping(Connection connection, DBMetadata metadata, String id,
															ImmutableFunctionalTerm target, String sql)
			throws SQLException, JSQLParserException, InvalidSelectQueryException, UnsupportedSelectQueryException {

		ImmutableList<SelectExpressionItem> queryColumns = getQueryColumns(metadata, sql);

		int arity = isURIRDFType(target.getTerm(1)) ? 1 : 2;
		Function templateAtom = (Function)((arity == 1)
				? target.getTerm(2)   // template is in the position of object
				: target.getTerm(1)); // template is in the position of predicate

		ImmutableList<SelectExpressionItem> templateColumns =
				getTemplateColumns(metadata.getQuotedIDFactory(), templateAtom.getTerms(), queryColumns);

		ImmutableList<SelectItem> newColumns = queryColumns.stream()
				.filter(c -> !templateColumns.contains(c))
				.collect(ImmutableCollectors.toList());
		if (newColumns.isEmpty())   // avoid empty SELECT clause
			newColumns = ImmutableList.of(new AllColumns());

		List<List<String>> templateValues = getTemplateValues(connection, sql, templateColumns);

		List<SQLPPTriplesMap> expandedMappings = new ArrayList<>(templateValues.size());

		for(List<String> values : templateValues) {
			// create a new  query with the changed projection and selection
			Expression whereClauseExtension = getWhereClauseExtension(templateColumns, values);

			Select select = (Select) CCJSqlParserUtil.parse(sql);
			PlainSelect plainSelect = (PlainSelect)select.getSelectBody();

			plainSelect.setSelectItems(newColumns);

			// whereClauseExtension is never null
			plainSelect.setWhere((plainSelect.getWhere() == null)
					? whereClauseExtension
					: new AndExpression(plainSelect.getWhere(), whereClauseExtension));

			OBDASQLQuery newSourceQuery =  MAPPING_FACTORY.getSQLQuery(select.toString());

			// construct new Target Query by expanding higher order atoms of the form
			// <pre>triple(t1, 'rdf:type', URI("http://example.org/{}", X))</pre>
			// to
			// <pre>http://example.org/cls(t1)</pre>, if X is t1
			// (similarly for properties)

			String predicateName = getPredicateName(templateAtom.getTerm(0), values);
			ImmutableFunctionalTerm newTarget = (arity == 1)
					? TERM_FACTORY.getImmutableFunctionalTerm(TERM_FACTORY.getClassPredicate(predicateName),
					target.getTerm(0))
					: TERM_FACTORY.getImmutableFunctionalTerm(TERM_FACTORY.getObjectPropertyPredicate(predicateName),
					target.getTerm(0), target.getTerm(2));

			String newId = IDGenerator.getNextUniqueID(id + "#");

			// TODO: see how to keep the provenance
			SQLPPTriplesMap mapping = new OntopNativeSQLPPTriplesMap(newId, newSourceQuery,
					ImmutableList.of(newTarget));

			expandedMappings.add(mapping);

			log.debug("Expanded Mapping: {}", mapping);
		}

		return expandedMappings;
	}



	private static ImmutableList<SelectExpressionItem> getQueryColumns(DBMetadata metadata, String sql)
			throws InvalidSelectQueryException, UnsupportedSelectQueryException {

		SelectQueryAttributeExtractor2 sqae = new SelectQueryAttributeExtractor2(metadata);
		PlainSelect plainSelect = sqae.getParsedSql(sql);
		ImmutableMap<QualifiedAttributeID, Variable> attributes = sqae.getQueryBodyAttributes(plainSelect);

		ImmutableList.Builder<SelectExpressionItem> builder = ImmutableList.builder();
		for (SelectItem si : plainSelect.getSelectItems()) {
			si.accept(new SelectItemVisitor() {
				@Override
				public void visit(AllColumns allColumns) {
					builder.addAll(sqae.expandStar(attributes).keySet().stream()
							.map(id -> new SelectExpressionItem(new Column(id.getAttribute().getSQLRendering())))
							.iterator());
				}

				@Override
				public void visit(AllTableColumns allTableColumns) {
					Table table = allTableColumns.getTable();
					builder.addAll(sqae.expandStar(attributes, table).keySet().stream()
							.map(id -> new SelectExpressionItem(new Column(table, id.getAttribute().getSQLRendering())))
							.iterator());
				}

				@Override
				public void visit(SelectExpressionItem selectExpressionItem) {
					builder.add(selectExpressionItem);
				}
			});
		}

		return builder.build();
	}

	private static List<List<String>> getTemplateValues(Connection connection, String sql,
														ImmutableList<SelectExpressionItem> templateColumns)
			throws SQLException, JSQLParserException {


		// The query for params is almost the same with the original source query, except that
		// we only need to distinct project the columns needed for the template expansion

		Select select = (Select)CCJSqlParserUtil.parse(sql);
		PlainSelect plainSelect = (PlainSelect)select.getSelectBody();

		plainSelect.setDistinct(new Distinct());
		plainSelect.setSelectItems(ImmutableList.copyOf(templateColumns)); // SelectExpressionItem -> SelectItem

		String distinctParamsSQL = select.toString();

		List<List<String>> templateValues = new ArrayList<>();
		try (Statement st = connection.createStatement()) {
			try (ResultSet rs = st.executeQuery(distinctParamsSQL)) {
				int size = templateColumns.size();
				while (rs.next()) {
					List<String> params = new ArrayList<>(size);
					for (int i = 1; i <= size; i++)
						params.add(rs.getString(i));

					templateValues.add(params);
				}
			}
		}
		return templateValues;
	}

	/**
	 * check if the term is {@code URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")}
	 * @param term
	 * @return
	 */
	private static boolean isURIRDFType(Term term) {
		if (term instanceof Function) {
			Function func = (Function) term;
			if (func.getArity() == 1 && (func.getFunctionSymbol() instanceof URITemplatePredicate)) {
				Term t0 = func.getTerm(0);
				if (t0 instanceof ValueConstant)
					return ((ValueConstant) t0).getValue().equals(IriConstants.RDF_TYPE);
			}
		}
		return false;
	}


	private static Expression getWhereClauseExtension(List<SelectExpressionItem> templateColumns,
													  List<String> values) {
		Expression selection = null;
		int size = templateColumns.size(); // both lists are ArrayLists of the same size
		for (int j = 0; j < size; j++) {
			BinaryExpression condition = new EqualsTo();
			condition.setLeftExpression(templateColumns.get(j).getExpression());
			condition.setRightExpression(new StringValue("'" + values.get(j) + "'"));

			selection = (selection != null) ? new AndExpression(selection, condition) : condition;
		}
		return selection;
	}


	/**
	 * This method get the columns which will be used for the predicate template
	 *
	 * @param templateTerms
	 * @param queryColumns
	 * @return
	 */
	private static ImmutableList<SelectExpressionItem> getTemplateColumns(QuotedIDFactory idfac, List<Term> templateTerms,
																		  List<SelectExpressionItem> queryColumns) {

		ImmutableMap<QuotedID, SelectExpressionItem> lookup = queryColumns.stream()
				.collect(ImmutableCollectors.toMap(
						si -> SelectQueryAttributeExtractor2.getSelectItemAliasedId(idfac, si),
						si -> si));

		List<QuotedID> templateColumns = getTemplateColumnNames(idfac, templateTerms);
		return templateColumns.stream()
				.map(id -> {
					SelectExpressionItem si = lookup.get(id);
					if (si == null)
						throw new IllegalArgumentException("The placeholder " + id +
								" in the target does not occur in the body of the mapping");
					return si;
				})
				.collect(ImmutableCollectors.toList());
	}

	/**
	 *
	 * This method extracts the variables in the template from the atom
	 * <p>
	 * Example:
	 * <p>
	 * Input templateAtom:
	 * <pre>URI("http://example.org/{}/{}", X, Y)</pre>
	 *
	 * Output: [X, Y]
	 * <p>
	 *
	 * @param templateTerms
	 * @return list of variables
	 */
	private static ImmutableList<QuotedID> getTemplateColumnNames(QuotedIDFactory idfac, List<Term> templateTerms) {

		int len = templateTerms.size();
		if (len == 1) { // the case of <{varUri}>
			Term uri = templateTerms.get(0);
			if (uri instanceof Variable)
				return ImmutableList.of(idfac.createAttributeID(((Variable) uri).getName()));

			throw new IllegalArgumentException("No variables could be found for this metamapping." +
					"Check that the variable in the metamapping is enclosed in a URI, for instance, " +
					"http://.../{var}");
		}
		else {
			ImmutableList.Builder<QuotedID> vars = ImmutableList.builder();
			// index 0 is for the URI template term
			for (int i = 1; i < len; i++)
				vars.add(idfac.createAttributeID(((Variable) templateTerms.get(i)).getName()));
			return vars.build();
		}
	}

	private static String getPredicateName(Term templateTerm, List<String> values) {
		if (templateTerm instanceof Variable) {
			return values.get(0);
		}
		else {
			String uriTemplate = ((ValueConstant) templateTerm).getValue();
			return URITemplates.format(uriTemplate, values);
		}
	}
}
