package it.unibz.inf.ontop.injection.impl;


import it.unibz.inf.ontop.datalog.IQ2DatalogTranslator;
import it.unibz.inf.ontop.datalog.UnionFlattener;
import it.unibz.inf.ontop.iq.executor.construction.ConstructionNodeCleaningExecutor;
import it.unibz.inf.ontop.iq.executor.expression.PushDownBooleanExpressionExecutor;
import it.unibz.inf.ontop.iq.executor.expression.PushUpBooleanExpressionExecutor;
import it.unibz.inf.ontop.iq.executor.groundterm.GroundTermRemovalFromDataNodeExecutor;
import it.unibz.inf.ontop.iq.executor.join.InnerJoinExecutor;
import it.unibz.inf.ontop.iq.executor.leftjoin.LeftJoinExecutor;
import it.unibz.inf.ontop.iq.executor.leftjoin.LeftJoinRightChildNormalizationAnalyzer;
import it.unibz.inf.ontop.iq.executor.projection.ProjectionShrinkingExecutor;
import it.unibz.inf.ontop.iq.executor.pullout.PullVariableOutOfDataNodeExecutor;
import it.unibz.inf.ontop.iq.executor.pullout.PullVariableOutOfSubTreeExecutor;
import it.unibz.inf.ontop.iq.executor.substitution.SubstitutionPropagationExecutor;
import it.unibz.inf.ontop.iq.executor.union.FlattenUnionExecutor;
import it.unibz.inf.ontop.iq.executor.union.UnionLiftExecutor;
import it.unibz.inf.ontop.injection.OntopOptimizationConfiguration;
import it.unibz.inf.ontop.injection.OntopOptimizationSettings;
import it.unibz.inf.ontop.iq.optimizer.*;
import it.unibz.inf.ontop.iq.tools.UnionBasedQueryMerger;
import it.unibz.inf.ontop.datalog.DatalogProgram2QueryConverter;

public class OntopOptimizationModule extends OntopAbstractModule {

    private OntopOptimizationConfiguration configuration;

    protected OntopOptimizationModule(OntopOptimizationConfiguration configuration) {
        super(configuration.getSettings());
        // Temporary (will be dropped)
        this.configuration = configuration;
    }


    @Override
    protected void configure() {
        bind(OntopOptimizationSettings.class).toInstance(configuration.getSettings());

        // Executors
        bindFromSettings(InnerJoinExecutor.class);
        bindFromSettings(SubstitutionPropagationExecutor.class);
        bindFromSettings(PushDownBooleanExpressionExecutor.class);
        bindFromSettings(PushUpBooleanExpressionExecutor.class);
        bindFromSettings(GroundTermRemovalFromDataNodeExecutor.class);
        bindFromSettings(PullVariableOutOfDataNodeExecutor.class);
        bindFromSettings(PullVariableOutOfSubTreeExecutor.class);
        bindFromSettings(UnionBasedQueryMerger.class);
        bindFromSettings(UnionLiftExecutor.class);
        bindFromSettings(LeftJoinExecutor.class);
        bindFromSettings(ProjectionShrinkingExecutor.class);
        bindFromSettings(FlattenUnionExecutor.class);
        bindFromSettings(ConstructionNodeCleaningExecutor.class);
        bindFromSettings(DatalogProgram2QueryConverter.class);
        bindFromSettings(InnerJoinOptimizer.class);
        bindFromSettings(JoinLikeOptimizer.class);
        bindFromSettings(LeftJoinOptimizer.class);
        bindFromSettings(BindingLiftOptimizer.class);
        bindFromSettings(IQ2DatalogTranslator.class);
        bindFromSettings(LeftJoinRightChildNormalizationAnalyzer.class);
        bindFromSettings(UnionAndBindingLiftOptimizer.class);
        bindFromSettings(UnionFlattener.class);
        bindFromSettings(PushDownBooleanExpressionOptimizer.class);
        bindFromSettings(PushUpBooleanExpressionOptimizer.class);

        // Releases the configuration (enables some GC)
        this.configuration = null;
    }
}
