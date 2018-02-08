package it.unibz.inf.ontop.iq.optimizer;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.node.ConstructionNode;
import it.unibz.inf.ontop.iq.node.ExtensionalDataNode;
import it.unibz.inf.ontop.iq.node.UnionNode;
import it.unibz.inf.ontop.model.atom.AtomPredicate;
import it.unibz.inf.ontop.model.atom.DistinctVariableOnlyDataAtom;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import org.junit.Test;

import static it.unibz.inf.ontop.NoDependencyTestDBMetadata.*;
import static it.unibz.inf.ontop.OptimizationTestingTools.*;
import static junit.framework.TestCase.assertTrue;

public class MappingUnionNormalizerTest {

    private final static AtomPredicate ANS2_PREDICATE = ATOM_FACTORY.getAtomPredicate("ans", 2);
    private final static Variable W = TERM_FACTORY.getVariable("W");
    private final static Variable X = TERM_FACTORY.getVariable("X");
    private final static Variable Y = TERM_FACTORY.getVariable("Y");
    private final static Variable Z = TERM_FACTORY.getVariable("Z");

    private final static DistinctVariableOnlyDataAtom PROJECTION_ATOM1 = ATOM_FACTORY.getDistinctVariableOnlyDataAtom
            (ANS2_PREDICATE, X, Y);
    private final static DistinctVariableOnlyDataAtom PROJECTION_ATOM2 = ATOM_FACTORY.getDistinctVariableOnlyDataAtom
            (ANS2_PREDICATE, W, Z);

    private final static ExtensionalDataNode DATA_NODE1 = IQ_FACTORY.createExtensionalDataNode(
            ATOM_FACTORY.getDataAtom(TABLE4_AR3, X, Y, Z)
    );
    private final static ExtensionalDataNode DATA_NODE2 = IQ_FACTORY.createExtensionalDataNode(
            ATOM_FACTORY.getDataAtom(TABLE5_AR3, X, Y, Z)
    );
    private final static ExtensionalDataNode DATA_NODE3 = IQ_FACTORY.createExtensionalDataNode(
            ATOM_FACTORY.getDataAtom(TABLE6_AR3, X, Y, Z)
    );
    private final static ExtensionalDataNode DATA_NODE4 = IQ_FACTORY.createExtensionalDataNode(
            ATOM_FACTORY.getDataAtom(TABLE1_AR2, Y, Z)
    );
    private final static ExtensionalDataNode DATA_NODE5 = IQ_FACTORY.createExtensionalDataNode(
            ATOM_FACTORY.getDataAtom(TABLE2_AR2, Y, Z)
    );

    private static Constant uriTemplate1 = TERM_FACTORY.getConstantLiteral("http://example.org/ds1/{}");
    private static Constant uriTemplate2 = TERM_FACTORY.getConstantLiteral("http://example.org/ds1/{}{}");

    @Test
    public void testMergeUnions() {

        UnionNode unionNode1 = IQ_FACTORY.createUnionNode(ImmutableSet.of(X, Y));

        IQTree union2 = IQ_FACTORY.createNaryIQTree(
                IQ_FACTORY.createUnionNode(ImmutableSet.of(X, Y, Z)),
                ImmutableList.of(DATA_NODE1, DATA_NODE2)
        );
        IQTree union1 = IQ_FACTORY.createNaryIQTree(
                unionNode1,
                ImmutableList.of(union2, DATA_NODE3)
        );

        IQ iq = IQ_FACTORY.createIQ(PROJECTION_ATOM1, union1);
        System.out.println("\nBefore optimization: \n" + iq);

        IQ optimizedIQ = MAPPING_UNION_NORMALIZER.optimize(iq);
        System.out.println("\nAfter optimization: \n" + optimizedIQ);

        IQ expectedIQ = IQ_FACTORY.createIQ(
                iq.getProjectionAtom(),
                IQ_FACTORY.createNaryIQTree(
                        unionNode1,
                        ImmutableList.of(
                                DATA_NODE3,
                                DATA_NODE1,
                                DATA_NODE2
                        )));
        System.out.println("\nExpected: \n" + expectedIQ);

        assertTrue(optimizedIQ.equals(expectedIQ));
    }

    @Test
    public void testLiftUnion() {

        ImmutableSubstitution sub = SUBSTITUTION_FACTORY.getSubstitution(W, generateURI(X, Y));
        ConstructionNode cn = IQ_FACTORY.createConstructionNode(ImmutableSet.of(W, Z), sub);
        UnionNode unionNode = IQ_FACTORY.createUnionNode(ImmutableSet.of(X, Y, Z));

        IQTree union = IQ_FACTORY.createNaryIQTree(
                unionNode,
                ImmutableList.of(DATA_NODE1, DATA_NODE2)
        );

        IQTree c = IQ_FACTORY.createUnaryIQTree(
                cn,
                union
        );

        IQ iq = IQ_FACTORY.createIQ(PROJECTION_ATOM2, c);
        System.out.println("\nBefore optimization: \n" + iq);

        IQ optimizedIQ = MAPPING_UNION_NORMALIZER.optimize(iq);
        System.out.println("\nAfter optimization: \n" + optimizedIQ);


        UnionNode unionNode2 = IQ_FACTORY.createUnionNode(ImmutableSet.of(W, Z));

        IQTree c2 = IQ_FACTORY.createUnaryIQTree(
                cn,
                DATA_NODE1
        );
        IQTree c3 = IQ_FACTORY.createUnaryIQTree(
                cn,
                DATA_NODE2
        );
        IQTree union2 = IQ_FACTORY.createNaryIQTree(
                unionNode2,
                ImmutableList.of(c2, c3)
        );

        IQ expectedIQ = IQ_FACTORY.createIQ(PROJECTION_ATOM2, union2);
        System.out.println("\nExpected: \n" + expectedIQ);

        assertTrue((optimizedIQ.equals(expectedIQ)));
    }

    @Test
    public void testLiftUnionAndMergeCn() {

        ImmutableSubstitution sub1 = SUBSTITUTION_FACTORY.getSubstitution(W, generateURI(X, Y));
        ImmutableSubstitution sub2 = SUBSTITUTION_FACTORY.getSubstitution(X, uriTemplate1);
        ConstructionNode cn1 = IQ_FACTORY.createConstructionNode(ImmutableSet.of(W, Z), sub1);
        ConstructionNode cn2 = IQ_FACTORY.createConstructionNode(ImmutableSet.of(X, Y, Z), sub2);
        UnionNode unionNode = IQ_FACTORY.createUnionNode(ImmutableSet.of(X, Y, Z));

        IQTree c2 = IQ_FACTORY.createUnaryIQTree(
                cn2,
                DATA_NODE4
        );
        IQTree c3 = IQ_FACTORY.createUnaryIQTree(
                cn2,
                DATA_NODE5
        );
        IQTree union = IQ_FACTORY.createNaryIQTree(
                unionNode,
                ImmutableList.of(c2, c3)
        );
        IQTree c1 = IQ_FACTORY.createUnaryIQTree(
                cn1,
                union
        );

        IQ iq = IQ_FACTORY.createIQ(PROJECTION_ATOM2, c1);
        System.out.println("\nBefore optimization: \n" + iq);

        IQ optimizedIQ = MAPPING_UNION_NORMALIZER.optimize(iq);
        System.out.println("\nAfter optimization: \n" + optimizedIQ);


        UnionNode unionNode2 = IQ_FACTORY.createUnionNode(ImmutableSet.of(W, Z));
        ImmutableSubstitution sub3 = SUBSTITUTION_FACTORY.getSubstitution(W, generateURI(uriTemplate1, Y));
        ConstructionNode cn3 = IQ_FACTORY.createConstructionNode(ImmutableSet.of(W, Z), sub3);

        IQTree c4 = IQ_FACTORY.createUnaryIQTree(
                cn3,
                DATA_NODE4
        );
        IQTree c5 = IQ_FACTORY.createUnaryIQTree(
                cn3,
                DATA_NODE5
        );
        IQTree union2 = IQ_FACTORY.createNaryIQTree(
                unionNode2,
                ImmutableList.of(c4, c5)
        );

        IQ expectedIQ = IQ_FACTORY.createIQ(PROJECTION_ATOM2, union2);
        System.out.println("\nExpected: \n" + expectedIQ);

        assertTrue((optimizedIQ.equals(expectedIQ)));
    }

    @Test
    public void testLiftUnionAndMergeUnion() {

        ImmutableSubstitution sub1 = SUBSTITUTION_FACTORY.getSubstitution(X, uriTemplate1);
        ImmutableSubstitution sub2 = SUBSTITUTION_FACTORY.getSubstitution(W, generateURI(X, Y));
        ConstructionNode cn1 = IQ_FACTORY.createConstructionNode(ImmutableSet.of(X, Y, Z), sub1);
        ConstructionNode cn2 = IQ_FACTORY.createConstructionNode(ImmutableSet.of(W, Z), sub2);
        UnionNode unionNode1 = IQ_FACTORY.createUnionNode(ImmutableSet.of(Y, Z));
        UnionNode unionNode2 = IQ_FACTORY.createUnionNode(ImmutableSet.of(X, Y, Z));

        IQTree union1 = IQ_FACTORY.createNaryIQTree(
                unionNode1,
                ImmutableList.of(DATA_NODE1, DATA_NODE4)
        );
        IQTree c1 = IQ_FACTORY.createUnaryIQTree(
                cn1,
                DATA_NODE5
        );
        IQTree c2 = IQ_FACTORY.createUnaryIQTree(
                cn1,
                union1
        );
        IQTree union2 = IQ_FACTORY.createNaryIQTree(
                unionNode2,
                ImmutableList.of(c1, c2)
        );
        IQTree c3 = IQ_FACTORY.createUnaryIQTree(
                cn2,
                union2
        );


        IQ iq = IQ_FACTORY.createIQ(PROJECTION_ATOM2, c3);
        System.out.println("\nBefore optimization: \n" + iq);

        IQ optimizedIQ = MAPPING_UNION_NORMALIZER.optimize(iq);
        System.out.println("\nAfter optimization: \n" + optimizedIQ);


        UnionNode unionNode3 = IQ_FACTORY.createUnionNode(ImmutableSet.of(W, Z));
        ImmutableSubstitution sub3 = SUBSTITUTION_FACTORY.getSubstitution(W, generateURI(uriTemplate1, Y));
        ConstructionNode cn3 = IQ_FACTORY.createConstructionNode(ImmutableSet.of(W, Z), sub3);

        IQTree union3 = IQ_FACTORY.createNaryIQTree(
                unionNode3,
                ImmutableList.of(
                        IQ_FACTORY.createUnaryIQTree(
                                cn3,
                                DATA_NODE5
                        ),
                        IQ_FACTORY.createUnaryIQTree(
                                cn3,
                                DATA_NODE1
                        ),
                        IQ_FACTORY.createUnaryIQTree(
                                cn3,
                                DATA_NODE4
                        )));

        IQ expectedIQ = IQ_FACTORY.createIQ(PROJECTION_ATOM2, union3);
        System.out.println("\nExpected: \n" + expectedIQ);

        assertTrue((optimizedIQ.equals(expectedIQ)));
    }


    private static ImmutableFunctionalTerm generateURI(VariableOrGroundTerm... arguments) {
        return TERM_FACTORY.getImmutableFunctionalTerm(
                TERM_FACTORY.getURITemplatePredicate(arguments.length),
                arguments
        );
    }
}
