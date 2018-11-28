package it.unibz.inf.ontop.iq.node;

import it.unibz.inf.ontop.iq.transform.node.HomogeneousQueryNodeTransformer;

public interface RelaxedFlattenNode extends FlattenNode<RelaxedFlattenNode>{

    @Override
    RelaxedFlattenNode acceptNodeTransformer(HomogeneousQueryNodeTransformer transformer);

    @Override
    RelaxedFlattenNode clone();
}
