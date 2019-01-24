package it.unibz.inf.ontop.answering.reformulation.input.translation.impl;

/*
 * #%L
 * ontop-reformulation-core
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

import com.google.common.collect.*;
import it.unibz.inf.ontop.answering.reformulation.IRIDictionary;
import it.unibz.inf.ontop.datalog.*;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.exception.OntopInvalidInputQueryException;
import it.unibz.inf.ontop.exception.OntopUnsupportedInputQueryException;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.atom.AtomPredicate;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.*;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBFunctionSymbolFactory;
import it.unibz.inf.ontop.model.term.impl.ImmutabilityTools;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.TermTypeInference;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.vocabulary.SPARQL;
import it.unibz.inf.ontop.model.vocabulary.XPathFunction;
import it.unibz.inf.ontop.model.vocabulary.XSD;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.R2RMLIRISafeEncoder;
import it.unibz.inf.ontop.utils.UriTemplateMatcher;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static it.unibz.inf.ontop.model.term.functionsymbol.BooleanExpressionOperation.*;
import static it.unibz.inf.ontop.model.term.functionsymbol.ExpressionOperation.*;


/***
 * Translate a SPARQL algebra expression into a Datalog program that has the
 * same semantics. We use the built-in predicates Join and LeftJoin.
 * 
 * @author Roman Kontchakov
 */
public class SparqlAlgebraToDatalogTranslator {


    private static final Logger log = LoggerFactory.getLogger(SparqlAlgebraToDatalogTranslator.class);

	private final UriTemplateMatcher uriTemplateMatcher;
	private final IRIDictionary uriRef;
    private final AtomFactory atomFactory;
    private final TermFactory termFactory;
    private final TypeFactory typeFactory;
    private final FunctionSymbolFactory functionSymbolFactory;

    private final DatalogProgram program;
    private final DatalogFactory datalogFactory;
    private int predicateIdx = 0;
    private final org.apache.commons.rdf.api.RDF rdfFactory;
    private final it.unibz.inf.ontop.model.term.Constant valueNull;
    private final ImmutabilityTools immutabilityTools;

    /**
     * @param uriTemplateMatcher matches URIs to templates (comes from mappings)
     * @param iriDictionary maps URIs to their integer identifiers (used only in the Semantic Index mode)
     * @param termFactory
     * @param typeFactory
     * @param functionSymbolFactory
     * @param datalogFactory
     * @param immutabilityTools
     *
	 */
	SparqlAlgebraToDatalogTranslator(@Nonnull UriTemplateMatcher uriTemplateMatcher,
                                     @Nullable IRIDictionary iriDictionary,
                                     AtomFactory atomFactory, TermFactory termFactory, TypeFactory typeFactory,
                                     FunctionSymbolFactory functionSymbolFactory, DatalogFactory datalogFactory,
                                     ImmutabilityTools immutabilityTools,
                                     org.apache.commons.rdf.api.RDF rdfFactory) {
		this.uriTemplateMatcher = uriTemplateMatcher;
		this.uriRef = iriDictionary;
        this.atomFactory = atomFactory;
        this.termFactory = termFactory;
        this.typeFactory = typeFactory;
        this.functionSymbolFactory = functionSymbolFactory;
        this.datalogFactory = datalogFactory;
        this.immutabilityTools = immutabilityTools;

        this.program = this.datalogFactory.getDatalogProgram();
        this.rdfFactory = rdfFactory;
        this.valueNull = termFactory.getNullConstant();
    }

	/**
	 * Translate a given SPARQL query object to datalog program.
     *
     * IMPORTANT: this method should be called only once on each instance of the class
	 *
	 * @pq     SPARQL query object
	 * @return our representation of the SPARQL query (as a datalog program)
     *
     * TODO: return an IntermediateQuery instead!
	 */
	public InternalSparqlQuery translate(ParsedQuery pq) throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {

        if (predicateIdx != 0 || !program.getRules().isEmpty())
            throw new IllegalStateException("SparqlAlgebraToDatalogTranslator.translate can only be called once.");

		TupleExpr te = pq.getTupleExpr();
		log.debug("SPARQL algebra: \n{}", te);
        //System.out.println("SPARQL algebra: \n" + te);

        TranslationResult body = translate(te);

        List<Variable> answerVariables;
		if (pq instanceof ParsedTupleQuery || pq instanceof ParsedGraphQuery) {
            // order elements of the set in some way by converting it into the list
            answerVariables = new ArrayList<>(body.variables);
        }
		else {
            // ASK queries have no answer variables
            answerVariables = Collections.emptyList();
        }

        AtomPredicate pred = atomFactory.getRDFAnswerPredicate(answerVariables.size());
        Function head = termFactory.getFunction(pred, (List<Term>)(List<?>)answerVariables);
        appendRule(head, body.atoms);

        //System.out.println("PROGRAM\n" + program.program);
		return new InternalSparqlQuery(program, ImmutableList.copyOf(answerVariables));
	}

    private class TranslationResult {

        final ImmutableList<Function> atoms;
        final ImmutableSet<Variable> variables;
        final boolean isBGP;

        TranslationResult(ImmutableList<Function> atoms, ImmutableSet<Variable> variables, boolean isBGP) {
            this.atoms = atoms;
            this.variables = variables;
            this.isBGP = isBGP;
        }

        /**
         * Extends the current translation result with bindings coming from {@link Extension} (expr AS ?x) or {@link BindingSetAssignment} (VALUES in SPARQL)
         *
         * @param bindings   a stream of bindings. A binding is a pair of a variable, and a value/expression
         * @param varMapper  a function from bindings to {@link Variable}s
         * @param exprMapper a function maps a pair of a binding and a set variables to a {@link Term}
         * @param <T>        A class for binding. E.g. {@link org.eclipse.rdf4j.query.Binding} or {@link org.eclipse.rdf4j.query.algebra.ExtensionElem}
         * @return extended translation result
         */
        <T> TranslationResult extendWithBindings(Stream<T> bindings,
                                                 java.util.function.Function<? super T, Variable> varMapper,
                                                 BiFunctionWithUnsupportedException<? super T, ImmutableSet<Variable>, Term> exprMapper)
                throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {

            Set<Variable> vars = new HashSet<>(variables);

            List<Function> eqAtoms = new ArrayList<>();

            // Functional-style replaced because of OntopUnsupportedInputQueryException
            for(T b : bindings.collect(Collectors.toList())) {
                Term expr = exprMapper.apply(b, ImmutableSet.copyOf(vars));

                Variable v = varMapper.apply(b);
                if (!vars.add(v))
                    throw new IllegalArgumentException("Duplicate binding for variable " + v);

                eqAtoms.add(termFactory.getFunctionEQ(v, expr));
            }

            return new TranslationResult(getAtomsExtended(eqAtoms.stream()), ImmutableSet.copyOf(vars), false);
        }


        ImmutableList<Function> getAtomsExtendedWithNulls(ImmutableSet<Variable> allVariables) {
            Sets.SetView<Variable>  nullVariables = Sets.difference(allVariables, variables);
            if (nullVariables.isEmpty())
                return atoms;

            return getAtomsExtended(nullVariables.stream().map(v -> termFactory.getFunctionEQ(v, termFactory.getNullConstant())));
        }

        /**
         * Extends the atoms in current translation result with {@code extension}
         *
         * @param extension a stream of functions to be added
         * @return extended list of atoms
         */
        ImmutableList<Function> getAtomsExtended(Stream<Function> extension) {
            return ImmutableList.copyOf(Iterables.concat(atoms, extension.collect(Collectors.toList())));

//            ImmutableList.Builder builder = ImmutableList.<Function>builder().addAll(atoms)
//                    .addAll(nullVariables.stream().map(v -> termFactory.getFunctionEQ(v, OBDAVocabulary.NULL)).iterator());

//            return builder.build();
        }

    }

    private Function getFreshHead(List<Term> terms) {
        Predicate pred = datalogFactory.getSubqueryPredicate("" + predicateIdx, terms.size());
        predicateIdx++;
        return termFactory.getFunction(pred, terms);
    }

    private TranslationResult createFreshNode(ImmutableSet<Variable> vars) {
        Function head = getFreshHead(new ArrayList<>(vars));
        return new TranslationResult(ImmutableList.of(head), vars, false);
    }

    private Function wrapNonTriplePattern(TranslationResult sub) {
        if (sub.atoms.size() > 1 || sub.atoms.get(0).isAlgebraFunction()) {
            Function head = getFreshHead(new ArrayList<>(sub.variables));
            appendRule(head, sub.atoms);
            return head;
        }
        return sub.atoms.get(0);
    }

    private void appendRule(Function head, List<Function> body) {
        CQIE rule = datalogFactory.getCQIE(head, body);
        program.appendRule(rule);
    }


    private TranslationResult translate(TupleExpr node) throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {

        //System.out.println("node: \n" + node);

        if (node instanceof Slice) {   // SLICE algebra operation
            Slice slice = (Slice) node;
            MutableQueryModifiers modifiers = program.getQueryModifiers();
            modifiers.setOffset(slice.getOffset());
            modifiers.setLimit(slice.getLimit());
            return translate(slice.getArg());
        }
        else if (node instanceof Distinct) { // DISTINCT algebra operation
            Distinct distinct = (Distinct) node;
            program.getQueryModifiers().setDistinct();
            return translate(distinct.getArg());
        }
        else if (node instanceof Reduced) {  // REDUCED algebra operation
            Reduced reduced = (Reduced) node;
            return translate(reduced.getArg());
        }
        else if (node instanceof Order) {   // ORDER algebra operation
            Order order = (Order) node;
            MutableQueryModifiers modifiers = program.getQueryModifiers();
            for (OrderElem c : order.getElements()) {
                ValueExpr expression = c.getExpr();
                if (!(expression instanceof Var))
                    throw new OntopUnsupportedInputQueryException("Error translating ORDER BY. "
                            + "The current implementation can only sort by variables. "
                            + "This query has a more complex expression '" + expression + "'");

                Var v = (Var) expression;
                Variable var = termFactory.getVariable(v.getName());
                int direction = c.isAscending() ? OrderCondition.ORDER_ASCENDING
                        : OrderCondition.ORDER_DESCENDING;
                modifiers.addOrderCondition(var, direction);
            }
            return translate(order.getArg());
        }
        else if (node instanceof StatementPattern) { // triple pattern
            return translateTriplePattern((StatementPattern) node);
        }
        else if (node instanceof SingletonSet) {
            // the empty BGP has no variables and gives a single solution mapping on every non-empty graph
            return new TranslationResult(ImmutableList.of(), ImmutableSet.of(), true);
        }
        else if (node instanceof Join) {     // JOIN algebra operation
            Join join = (Join) node;
            TranslationResult a1 = translate(join.getLeftArg());
            TranslationResult a2 = translate(join.getRightArg());
            ImmutableSet<Variable> vars = Sets.union(a1.variables, a2.variables).immutableCopy();

            if (a1.isBGP && a2.isBGP) {             // collect triple patterns into BGPs
                ImmutableList<Function> atoms =
                        ImmutableList.<Function>builder().addAll(a1.atoms).addAll(a2.atoms).build();
                return new TranslationResult(atoms, vars, true);
            }
            else {
                Function body = datalogFactory.getSPARQLJoin(wrapNonTriplePattern(a1),
                        wrapNonTriplePattern(a2));

                return new TranslationResult(ImmutableList.of(body), vars, false);
            }
        }
        else if (node instanceof LeftJoin) {  // OPTIONAL algebra operation
            LeftJoin lj = (LeftJoin) node;
            TranslationResult a1 = translate(lj.getLeftArg());
            TranslationResult a2 = translate(lj.getRightArg());
            ImmutableSet<Variable> vars = Sets.union(a1.variables, a2.variables).immutableCopy();

            Function body = datalogFactory.getSPARQLLeftJoin(wrapNonTriplePattern(a1),
                    wrapNonTriplePattern(a2));

            ValueExpr expr = lj.getCondition();
            if (expr != null) {
                Function f = getFilterExpression(expr, vars);
                body.getTerms().add(f);
            }

            return new TranslationResult(ImmutableList.of(body), vars, false);
        }
        else if (node instanceof Union) {   // UNION algebra operation
            Union union = (Union) node;
            TranslationResult a1 = translate(union.getLeftArg());
            TranslationResult a2 = translate(union.getRightArg());
            ImmutableSet<Variable> vars = Sets.union(a1.variables, a2.variables).immutableCopy();

            TranslationResult res = createFreshNode(vars);
            appendRule(res.atoms.get(0), a1.getAtomsExtendedWithNulls(vars));
            appendRule(res.atoms.get(0), a2.getAtomsExtendedWithNulls(vars));
            return res;
        }
        else if (node instanceof Filter) {   // FILTER algebra operation
            Filter filter = (Filter) node;
            TranslationResult a = translate(filter.getArg());

            Function f = getFilterExpression(filter.getCondition(), a.variables);
            ImmutableList<Function> atoms = ImmutableList.<Function>builder().addAll(a.atoms).add(f).build();
            // TODO: split ANDs in the FILTER?

            return new TranslationResult(atoms, a.variables, false);
        }
        else if (node instanceof Projection) {  // PROJECT algebra operation
            Projection projection = (Projection) node;
            TranslationResult sub = translate(projection.getArg());

            List<ProjectionElem> pes = projection.getProjectionElemList().getElements();
            // the two lists are required to synchronise the order of variables
            List<Term> sVars = new ArrayList<>(pes.size());
            List<Term> tVars = new ArrayList<>(pes.size());
            boolean noRenaming = true;
            for (ProjectionElem pe : pes) {
                Variable sVar = termFactory.getVariable(pe.getSourceName());
                if (!sub.variables.contains(sVar))
                    throw new IllegalArgumentException("Projection source of " + pe
                            + " not found in " + projection.getArg());
                sVars.add(sVar);

                Variable tVar = termFactory.getVariable(pe.getTargetName());
                tVars.add(tVar);

                if (!sVar.equals(tVar))
                    noRenaming = false;
            }
            if (noRenaming && sVars.containsAll(sub.variables)) // neither projection nor renaming
                return sub;

            // Preserves the variable order of the SPARQL query (good practice)
            ImmutableSet<Variable> vars = ImmutableSortedSet.copyOf(
                    tVars.stream().map(t -> (Variable) t).collect(Collectors.toList()));

            if (noRenaming)
                return new TranslationResult(sub.atoms, vars, false);

            Function head = getFreshHead(sVars);
            appendRule(head, sub.atoms);

            Function atom = termFactory.getFunction(head.getFunctionSymbol(), tVars);
            return new TranslationResult(ImmutableList.of(atom), vars, false);
        }
        else if (node instanceof Extension) {     // EXTEND algebra operation
            Extension extension = (Extension) node;
            TranslationResult sub = translate(extension.getArg());
            final Stream<ExtensionElem> nontrivialBindings = extension.getElements().stream()
                    // ignore EXTEND(P, v, v), which is sometimes introduced by Sesame SPARQL parser
                    .filter(ee -> !(ee.getExpr() instanceof Var && ee.getName().equals(((Var) ee.getExpr()).getName())));
            return sub.extendWithBindings(
                    nontrivialBindings,
                    ee -> termFactory.getVariable(ee.getName()),
                    (ee, vars) -> getExpression(ee.getExpr(), vars));
        }
        else if (node instanceof BindingSetAssignment) { // VALUES in SPARQL
            BindingSetAssignment values = (BindingSetAssignment) node;

            TranslationResult empty = new TranslationResult(ImmutableList.of(), ImmutableSet.of(), false);
            List<TranslationResult> bindings = new ArrayList<>();
            for (BindingSet bs :values.getBindingSets()) {
                bindings.add(empty.extendWithBindings(
                        StreamSupport.stream(bs.spliterator(), false),
                        be -> termFactory.getVariable(be.getName()),
                        (be, vars) -> getTermForLiteralOrIri(be.getValue())));
            }

            ImmutableSet<Variable> allVars = bindings.stream()
                    .flatMap(s -> s.variables.stream())
                    .collect(ImmutableCollectors.toSet());

            TranslationResult res = createFreshNode(allVars);
            bindings.forEach(p ->
                    appendRule(res.atoms.get(0), p.getAtomsExtendedWithNulls(allVars)));
            return res;
        }
        else if (node instanceof Group) {
            throw new OntopUnsupportedInputQueryException("GROUP BY is not supported yet");
        }
        throw new OntopUnsupportedInputQueryException("Not supported: " + node);
    }

    /**
     *
     * @param expr  expression
     * @param variables the set of variables that can occur in the expression
     *                  (the rest will be replaced with NULL)
     * @return
     */

    private Function getFilterExpression(ValueExpr expr, ImmutableSet<Variable> variables)
            throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {
        Term term = getExpression(expr, variables);
        // Effective Boolean Value (EBV): wrap in isTrue function if it is not a (Boolean) expression
        if (term instanceof Function) {
            Function f = (Function) term;
            ImmutableFunctionalTerm functionalTerm = (ImmutableFunctionalTerm) immutabilityTools.convertIntoImmutableTerm(f);
            if ((functionalTerm.getFunctionSymbol() instanceof SPARQLFunctionSymbol)
                    && functionalTerm.inferType()
                        .flatMap(TermTypeInference::getTermType)
                        .filter(t -> t instanceof RDFDatatype)
                        .filter(t -> ((RDFDatatype) t).isA(XSD.BOOLEAN))
                        .isPresent())
                return immutabilityTools.convertToMutableFunction(termFactory.getRDF2DBBooleanFunctionalTerm(functionalTerm));
            // TODO: deal with other functional terms
            return f;
        }
        return termFactory.getFunctionIsTrue(term);
    }

	private TranslationResult translateTriplePattern(StatementPattern triple) throws OntopUnsupportedInputQueryException {

        // A triple pattern is member of the set (RDF-T + V) x (I + V) x (RDF-T + V)
        // VarOrTerm ::=  Var | GraphTerm
        // GraphTerm ::=  iri | RDFLiteral | NumericLiteral | BooleanLiteral | BlankNode | NIL

        ImmutableSet.Builder<Variable> variables = ImmutableSet.builder();
        Function atom;

        Value s = triple.getSubjectVar().getValue();
        Value p = triple.getPredicateVar().getValue();
        Value o = triple.getObjectVar().getValue();

        Term sTerm = (s == null) ? getTermForVariable(triple.getSubjectVar(), variables) : getTermForLiteralOrIri(s);

		if (p == null) {
			//  term variable term .
            Term pTerm = getTermForVariable(triple.getPredicateVar(), variables);
            Term oTerm = (o == null) ? getTermForVariable(triple.getObjectVar(), variables) : getTermForLiteralOrIri(o);
			atom = atomFactory.getMutableTripleAtom(sTerm, pTerm, oTerm);
		}
		else if (p instanceof IRI) {
			if (p.equals(RDF.TYPE)) {
                Term oTerm;
					// term rdf:type variable .
                Term pTerm = termFactory.getConstantIRI(it.unibz.inf.ontop.model.vocabulary.RDF.TYPE);
                if (o == null) {
                    oTerm = getTermForVariable(triple.getObjectVar(), variables);

				}
				else if (o instanceof IRI) {
                    oTerm = termFactory.getConstantIRI(rdfFactory.createIRI(o.stringValue()));
				}
				else
					throw new OntopUnsupportedInputQueryException("Unsupported query syntax");
                atom = atomFactory.getMutableTripleAtom(sTerm, pTerm, oTerm);
			}
			else {
				// term uri term . (where uri is either an object or a datatype property)
				Term oTerm = (o == null) ? getTermForVariable(triple.getObjectVar(), variables) : getTermForLiteralOrIri(o);
                Term pTerm = termFactory.getConstantIRI(rdfFactory.createIRI(p.stringValue()));
                atom = atomFactory.getMutableTripleAtom(sTerm, pTerm, oTerm);
			}
		}
		else
			// if predicate is a variable or literal
			throw new OntopUnsupportedInputQueryException("Unsupported query syntax");

        return new TranslationResult(ImmutableList.of(atom), variables.build(), true);
	}

    private Term getTermForVariable(Var v, ImmutableSet.Builder<Variable> variables) {
        Variable var = termFactory.getVariable(v.getName());
        variables.add(var);
        return var;
    }

    private Term getTermForLiteralOrIri(Value v) throws OntopUnsupportedInputQueryException {

        if (v instanceof Literal)
            return getTermForLiteral((Literal) v);
        else if (v instanceof IRI)
            return getTermForIri((IRI)v, false);

        throw new OntopUnsupportedInputQueryException("The value " + v + " is not supported yet!");
    }

    private Term getTermForLiteral(Literal literal) throws OntopUnsupportedInputQueryException {
        IRI typeURI = literal.getDatatype();
        String value = literal.getLabel();
        Optional<String> lang = literal.getLanguage();

        if (lang.isPresent()) {
            return termFactory.getRDFLiteralMutableFunctionalTerm(termFactory.getDBStringConstant(value), lang.get());

        } else {
            RDFDatatype type;
             /*
              * default data type is xsd:string
              */
            if (typeURI == null) {
                type = typeFactory.getXsdStringDatatype();
            } else {
                type = typeFactory.getDatatype(rdfFactory.createIRI(typeURI.stringValue()));
            }

            if (type == null)
                // ROMAN (27 June 2016): type1 in open-eq-05 test would not be supported in OWL
                // the actual value is LOST here
                return immutabilityTools.convertToMutableTerm(
                        termFactory.getConstantIRI(rdfFactory.createIRI(typeURI.stringValue())));
            // old strict version:
            // throw new RuntimeException("Unsupported datatype: " + typeURI);

            // check if the value is (lexically) correct for the specified datatype
            if (!XMLDatatypeUtil.isValidValue(value, typeURI))
                throw new OntopUnsupportedInputQueryException("Invalid lexical form for datatype. Found: " + value);

            Term constant = termFactory.getDBStringConstant(value);

            return termFactory.getRDFLiteralMutableFunctionalTerm(constant, type);

        }
    }

    /**
     *
     * @param v URI object
     * @param unknownUrisToTemplates - the URIs are treated differently in triple patterns
     *                               and filter expressions (this will be normalised later)
     * @return term (URI template)
     */

    private Term getTermForIri(IRI v, boolean unknownUrisToTemplates) {

        // Guohui(07 Feb, 2018): this logic should probably be moved to a different place, since some percentage-encoded
        // string of an IRI might be a part of an IRI template, but not from database value.
         String uri = R2RMLIRISafeEncoder.decode(v.stringValue());
        //String uri = v.stringValue();

        if (uriRef != null) {  // if in the Semantic Index mode
            int id = uriRef.getId(uri);
            if (id < 0 && unknownUrisToTemplates)  // URI is not found and need to wrap it in a template
                return termFactory.getConstantIRI(rdfFactory.createIRI(uri));
            else
                return immutabilityTools.convertToMutableFunction(termFactory.getRDFFunctionalTerm(id));
        }
        else {
            return termFactory.getConstantIRI(rdfFactory.createIRI(uri));
        }
    }

    /**
     *
     * @param expr expression
     * @param variables the set of variables that can occur in the expression
     *                  (the rest will be replaced with NULL)
     * @return term
     */

	private Term getExpression(ValueExpr expr, ImmutableSet<Variable> variables) throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {

        // PrimaryExpression ::= BrackettedExpression | BuiltInCall | iriOrFunction |
        //                          RDFLiteral | NumericLiteral | BooleanLiteral | Var
        // iriOrFunction ::= iri ArgList?

		if (expr instanceof Var) {
            Var v = (Var) expr;
            Variable var = termFactory.getVariable(v.getName());
            return variables.contains(var) ? var : valueNull;
		} 
		else if (expr instanceof ValueConstant) {
			Value v = ((ValueConstant) expr).getValue();
            if (v instanceof Literal)
                return getTermForLiteral((Literal) v);
            else if (v instanceof IRI)
                return getTermForIri((IRI)v, true);

            throw new OntopUnsupportedInputQueryException("The value " + v + " is not supported yet!");
        }
        else if (expr instanceof Bound) {
            // BOUND (Sec 17.4.1.1)
            // xsd:boolean  BOUND (variable var)
            Var v = ((Bound) expr).getArg();
            Variable var = termFactory.getVariable(v.getName());
            return variables.contains(var)
                    ? termFactory.getFunction(
                            functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.BOUND, 1), var)
                    : termFactory.getRDFLiteralConstant("false", XSD.BOOLEAN);
        }
        else if (expr instanceof UnaryValueOperator) {
            Term term = getExpression(((UnaryValueOperator) expr).getArg(), variables);

            if (expr instanceof Not) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                        term);
            }
            else if (expr instanceof IsNumeric) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_NUMERIC, 1),
                        term);
            }
            else if (expr instanceof IsLiteral) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_LITERAL, 1),
                        term);
            }
            else if (expr instanceof IsURI) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_IRI, 1),
                        term);
            }
            else if (expr instanceof Str) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.STR, 1),
                        term);
            }
            else if (expr instanceof Datatype) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.DATATYPE, 1),
                        term);
            }
            else if (expr instanceof IsBNode) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_BLANK, 1),
                        term);
            }
            else if (expr instanceof Lang) {
                ValueExpr arg = ((UnaryValueOperator) expr).getArg();
                if (arg instanceof Var)
                    return termFactory.getFunction(
                            functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LANG, 1),
                            term);
                else
                    throw new RuntimeException("A variable or a value is expected in " + expr);
            }
            // other subclasses
            // IRIFunction: IRI (Sec 17.4.2.8) for constructing IRIs
            // IsNumeric:  isNumeric (Sec 17.4.2.4) for checking whether the argument is a numeric value
            // AggregateOperatorBase: Avg, Min, Max, etc.
            // Like:  ??
            // IsResource: ??
            // LocalName: ??
            // Namespace: ??
            // Label: ??
		}
		else if (expr instanceof BinaryValueOperator) {
            BinaryValueOperator bexpr = (BinaryValueOperator) expr;
            Term term1 = getExpression(bexpr.getLeftArg(), variables);
            Term term2 = getExpression(bexpr.getRightArg(), variables);

            if (expr instanceof And) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LOGICAL_AND, 2),
                        term1, term2);
            }
            else if (expr instanceof Or) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LOGICAL_OR, 2),
                        term1, term2);
            }
            else if (expr instanceof SameTerm) {
                // sameTerm (Sec 17.4.1.8)
                // Corresponds to the STRICT equality (same lexical value, same type)
                return termFactory.getFunctionStrictEQ(term1, term2);
            }
            else if (expr instanceof Regex) {
                // REGEX (Sec 17.4.3.14)
                // xsd:boolean  REGEX (string literal text, simple literal pattern)
                // xsd:boolean  REGEX (string literal text, simple literal pattern, simple literal flags)
                Regex reg = (Regex) expr;
                return (reg.getFlagsArg() != null)
                        ? termFactory.getFunction(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.REGEX, 3),
                                term1, term2,
                                getExpression(reg.getFlagsArg(), variables))
                        : termFactory.getFunction(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.REGEX, 2),
                                term1, term2);
            }
            else if (expr instanceof Compare) {
                // TODO: make it a SPARQLFunctionSymbol
                final FunctionSymbol p;

                /*
                 * TODO: update the Relational operations map and add EQ inside it.
                 */
                switch (((Compare) expr).getOperator()) {
                    case NE:
                        return termFactory.getFunction(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getFunction(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.EQ, 2),
                                        term1, term2));
                    case EQ:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.EQ, 2);
                        break;
                    default:
                        p = RelationalOperations.get(((Compare) expr).getOperator());
                }
                return termFactory.getFunction(p, term1, term2);
            }
            else if (expr instanceof MathExpr) {
                SPARQLFunctionSymbol f = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(
                        NumericalOperations.get(((MathExpr)expr).getOperator()), 2);
                return termFactory.getFunction(f, term1, term2);
            }
            /*
             * Restriction: the first argument must be LANG(...) and the second  a constant
             * (for guaranteeing that the langMatches logic is not delegated to the native query)
             */
            else if (expr instanceof LangMatches) {
                if ((!((term1 instanceof Function)
                        && ((Function) term1).getFunctionSymbol() instanceof LangSPARQLFunctionSymbol))
                        || (!((term2 instanceof Function)
                        // TODO: support "real" constants (not wrapped into a functional term)
                        && ((Function) term2).getFunctionSymbol() instanceof RDFTermFunctionSymbol)) ) {
                    throw new OntopUnsupportedInputQueryException("The function langMatches is " +
                            "only supported with lang(..) function for the first argument and a constant for the second");
                }

                SPARQLFunctionSymbol langMatchesFunctionSymbol = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LANG_MATCHES, 2);

                return termFactory.getExpression(
                        functionSymbolFactory.getRDF2DBBooleanFunctionSymbol(),
                        termFactory.getFunction(langMatchesFunctionSymbol, term1, term2));
            }
        }
		else if (expr instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) expr;

            int arity = f.getArgs().size();
            List<Term> terms = new ArrayList<>(arity);
            for (ValueExpr a : f.getArgs())
                terms.add(getExpression(a, variables));

            // New approach
            Optional<SPARQLFunctionSymbol> optionalFunctionSymbol = functionSymbolFactory.getSPARQLFunctionSymbol(
                    f.getURI(), terms.size());

            if (optionalFunctionSymbol.isPresent()) {
                return termFactory.getFunction(optionalFunctionSymbol.get(), terms);
            }

            // Old approach
            FunctionSymbol p = XPathFunctions.get(f.getURI());
            if (p != null) {
                if (arity != p.getArity())
                    throw new OntopInvalidInputQueryException(
                            "Wrong number of arguments (found " + terms.size() + ", only " +
                                    p.getArity() + "supported) for SPARQL " + f.getURI() + "function");

                return termFactory.getFunction(p, terms);
            }
		}
        // other subclasses
        // SubQueryValueOperator
        // If
        // BNodeGenerator
        // NAryValueOperator (ListMemberOperator and Coalesce)
		throw new OntopUnsupportedInputQueryException("The expression " + expr + " is not supported yet!");
	}

    // XPath 1.0 functions (XPath 1.1 has variants with more arguments)
    private static final ImmutableMap<String, FunctionSymbol> XPathFunctions =
            new ImmutableMap.Builder<String, FunctionSymbol>()
                    /*
                     * Numerical functions
                     */
                    .put("http://www.w3.org/2005/xpath-functions#numeric-abs", ABS)
                    .put("http://www.w3.org/2005/xpath-functions#numeric-ceil", CEIL)
                    .put("http://www.w3.org/2005/xpath-functions#numeric-floor", FLOOR)
                    .put("http://www.w3.org/2005/xpath-functions#numeric-round", ROUND)
                    .put("RAND", RAND)
                    /*
                     * Datetime functions
                     */
                    .put("http://www.w3.org/2005/xpath-functions#year-from-dateTime", YEAR)
                    .put("http://www.w3.org/2005/xpath-functions#day-from-dateTime", DAY)
                    .put("http://www.w3.org/2005/xpath-functions#month-from-dateTime", MONTH)
                    .put("http://www.w3.org/2005/xpath-functions#hours-from-dateTime", HOURS)
                    .put("http://www.w3.org/2005/xpath-functions#minutes-from-dateTime", MINUTES)
                    .put("http://www.w3.org/2005/xpath-functions#seconds-from-dateTime", SECONDS)
                    .put("NOW", NOW)
                    .put("TZ", TZ)
                    .build();


	private static final ImmutableMap<Compare.CompareOp, BooleanExpressionOperation> RelationalOperations =
			new ImmutableMap.Builder<Compare.CompareOp, BooleanExpressionOperation>()
				.put(Compare.CompareOp.GE, GTE)
				.put(Compare.CompareOp.GT, GT)
				.put(Compare.CompareOp.LE, LTE)
				.put(Compare.CompareOp.LT, LT)
				.build();

	private static final ImmutableMap<MathExpr.MathOp, String> NumericalOperations =
			new ImmutableMap.Builder<MathExpr.MathOp, String>()
			.put(MathExpr.MathOp.PLUS, SPARQL.NUMERIC_ADD)
			.put(MathExpr.MathOp.MINUS, SPARQL.NUMERIC_SUBSTRACT)
			.put(MathExpr.MathOp.MULTIPLY, SPARQL.NUMERIC_MULTIPLY)
			.put(MathExpr.MathOp.DIVIDE, SPARQL.NUMERIC_DIVIDE)
			.build();


	@FunctionalInterface
	private interface BiFunctionWithUnsupportedException<T, U, R> {

	    R apply(T v1, U v2) throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException;

    }
}
