package org.umlg.sqlg;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.umlg.sqlg.test.*;
import org.umlg.sqlg.test.aggregate.TestAggregate;
import org.umlg.sqlg.test.aggregate.TestGroupCount;
import org.umlg.sqlg.test.aggregate.TestMax;
import org.umlg.sqlg.test.batch.*;
import org.umlg.sqlg.test.branchstep.TestSqlgBranchStep;
import org.umlg.sqlg.test.complex.TestComplex;
import org.umlg.sqlg.test.complex.TestGithub;
import org.umlg.sqlg.test.edgehas.TestEdgeHas;
import org.umlg.sqlg.test.edges.*;
import org.umlg.sqlg.test.event.TestTinkerPopEvent;
import org.umlg.sqlg.test.filter.and.TestAndStep;
import org.umlg.sqlg.test.filter.and.barrier.TestAndStepBarrier;
import org.umlg.sqlg.test.filter.connectivestep.TestAndandOrStep;
import org.umlg.sqlg.test.filter.not.barrier.TestNotStepBarrier;
import org.umlg.sqlg.test.filter.or.TestOrStep;
import org.umlg.sqlg.test.filter.or.TestOrStepAfterVertexStepBarrier;
import org.umlg.sqlg.test.filter.or.barrier.TestOrStepBarrier;
import org.umlg.sqlg.test.graph.MidTraversalGraphTest;
import org.umlg.sqlg.test.graph.TestEmptyGraph;
import org.umlg.sqlg.test.graph.TestGraphStepWithIds;
import org.umlg.sqlg.test.gremlincompile.*;
import org.umlg.sqlg.test.index.TestIndex;
import org.umlg.sqlg.test.index.TestIndexTopologyTraversal;
import org.umlg.sqlg.test.io.TestIo;
import org.umlg.sqlg.test.io.TestIoEdge;
import org.umlg.sqlg.test.json.TestJson;
import org.umlg.sqlg.test.json.TestJsonUpdate;
import org.umlg.sqlg.test.labels.TestHasLabelAndId;
import org.umlg.sqlg.test.labels.TestLabelLength;
import org.umlg.sqlg.test.labels.TestLabelsSchema;
import org.umlg.sqlg.test.labels.TestMultipleLabels;
import org.umlg.sqlg.test.localdate.TestLocalDate;
import org.umlg.sqlg.test.localdate.TestLocalDateArray;
import org.umlg.sqlg.test.localvertexstep.*;
import org.umlg.sqlg.test.match.TestMatch;
import org.umlg.sqlg.test.memory.TestMemoryUsage;
import org.umlg.sqlg.test.mod.*;
import org.umlg.sqlg.test.process.dropstep.TestDropStep;
import org.umlg.sqlg.test.process.dropstep.TestDropStepBarrier;
import org.umlg.sqlg.test.process.dropstep.TestDropStepTruncate;
import org.umlg.sqlg.test.properties.TestEscapedValues;
import org.umlg.sqlg.test.remove.TestRemoveEdge;
import org.umlg.sqlg.test.repeatstep.TestUnoptimizedRepeatStep;
import org.umlg.sqlg.test.rollback.TestRollback;
import org.umlg.sqlg.test.sack.TestSack;
import org.umlg.sqlg.test.sample.TestSample;
import org.umlg.sqlg.test.schema.*;
import org.umlg.sqlg.test.topology.*;
import org.umlg.sqlg.test.travers.TestTraversals;
import org.umlg.sqlg.test.tree.TestColumnNamePropertyNameMapScope;
import org.umlg.sqlg.test.vertex.*;
import org.umlg.sqlg.test.vertexout.TestVertexOutWithHas;
import org.umlg.sqlg.test.where.TestTraversalFilterStepBarrier;

/**
 * Date: 2014/07/16
 * Time: 12:08 PM
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        TestAddVertexViaMap.class,
        TestAllEdges.class,
        TestAllVertices.class,
        TestArrayProperties.class,
        TestCountVerticesAndEdges.class,
        TestDeletedVertex.class,
        TestEdgeCreation.class,
        TestEdgeToDifferentLabeledVertexes.class,
        TestGetById.class,
        TestHas.class,
        TestHasLabelAndId.class,
        TestLoadArrayProperties.class,
        TestLoadElementProperties.class,
        TestLoadSchema.class,
        TestPool.class,
        TestRemoveElement.class,
        TestSetProperty.class,
        TestVertexCreation.class,
        TestVertexEdgeSameName.class,
        TestVertexNavToEdges.class,
        // are these tests gone?
        //TestByteArray.class,
        //TestQuery.class,
        TestSchema.class,
        TestIndex.class,
        TestVertexOutWithHas.class,
        TestEdgeHas.class,
        TestBatch.class,
        TestBatchNormalUpdate.class,
        TestMultiThreadedBatch.class,
        TestMultiThread.class,
        TestMultipleThreadMultipleJvm.class,

        TestRemoveEdge.class,
        TestEdgeSchemaCreation.class,
        TestRollback.class,

        TestNewVertex.class,
        TestEdgeCache.class,
        TestVertexCache.class,
        TestTinkerpopBug.class,
        TestLoadSchemaViaNotify.class,
        TestCreateEdgeBetweenVertices.class,
        TestRemovedVertex.class,
        TestCaptureSchemaTableEdges.class,
        TestGremlinCompileWithHas.class,
        TestGremlinCompileE.class,
        TestEmptyGraph.class,
        TestOutE.class,
        TestForeignKeysAreOptional.class,
        TestGremlinCompileWithAs.class,
        TestGremlinCompileWithInOutV.class,
        TestGremlinCompileV.class,
        TestGremlinCompileGraphStep.class,
        TestGremlinCompileGraphV.class,
        TestGremlinCompileWhere.class,
        TestGremlinCompileTextPredicate.class,
        TestGremlinCompileFullTextPredicate.class,
        TestGremlinCompileChoose.class,
        TestGremlinCompileVertexStep.class,
        TestGremlinCompileWhereLocalDate.class,
        TestColumnNameTranslation.class,
        TestGraphStepOrderBy.class,
        TestVertexStepOrderBy.class,
        TestPathStep.class,
        TestLocalDate.class,
        TestLocalDateArray.class,
        TestBatchStreamVertex.class,
        TestBatchStreamEdge.class,
        TestJson.class,
        TestSchemaManagerGetTablesFor.class,
        TestBatchServerSideEdgeCreation.class,
        TestBatchedStreaming.class,
        TestBulkWithin.class,
        TestBulkWithout.class,
        TestRemoveProperty.class,
        TestSchemaManagerGetTablesFor.class,
        TestAggregate.class,
        TestTreeStep.class,
        TestRepeatStepGraphOut.class,
        TestRepeatStepGraphIn.class,
        TestRepeatStepVertexOut.class,
        TestRepeatStepGraphBoth.class,
        TestRepeatStepWithLabels.class,
        TestGraphStepWithIds.class,
        TestOtherVertex.class,
        TestGremlinMod.class,
        TestTopologyUpgrade.class,
        TestTopologyMultipleGraphs.class,
        TestTraversals.class,
        TestGremlinOptional.class,
        TestAlias.class,
        TestGithub.class,
        TestLocalVertexStepOptional.class,
        TestLocalVertexStepRepeatStep.class,
        TestLocalEdgeVertexStep.class,
        TestLocalEdgeOtherVertexStep.class,
        TestBatchNormalDateTime.class,
        TestBatchEdgeDateTime.class,
        TestBatchJson.class,
        TestMemoryUsage.class,
        TestBatchStreamTemporaryVertex.class,
        TestBatchNormalPrimitiveArrays.class,
        TestBatchNormalPrimitive.class,
        TestBatchNormalUpdatePrimitiveArrays.class,
        TestJsonUpdate.class,
        TestBatchNormalUpdateDateTime.class,
        TestOptionalWithOrder.class,
        TestMultipleLabels.class,
        TestColumnNamePropertyNameMapScope.class,
        TestJNDIInitialization.class,
        TestSchemaTableTreeAndHasContainer.class,
        TestEscapedValues.class,
        TestRepeatStepOnEdges.class,
        TestLoadingAdjacent.class,
        TestLabelsSchema.class,
        MidTraversalGraphTest.class,
        //TODO fails, issue #65
//        TestEdgeFromDifferentSchema.class
        TestBatchModeMultipleGraphs.class,
        TestDetachedEdge.class,
        TestSchemaEagerCreation.class,
        TestIndexTopologyTraversal.class,
        TestNotifyJson.class,
        TestGlobalUniqueIndex.class,
        TestBatchGlobalUniqueIndexes.class,
        TestVertexEdges.class,
        TestSqlgSchema.class,
        TestValidateTopology.class,
        TestBatchNormalUpdateDateTimeArrays.class,
        TestTopologyChangeListener.class,
        TestRangeLimit.class,
        TestReplacedStepEmitComparator.class,
        TestLocalStepCompile.class,
        TestLocalVertexStepLimit.class,
        TestLocalVertexStepOptionalWithOrder.class,
        TestOptionalWithRange.class,
        TestRepeatWithOrderAndRange.class,
        TestMatch.class,
        TestSqlgBranchStep.class,
        TestLocalVertexStepWithOrder.class,
        TestMax.class,
        TestGroupCount.class,
        TestSack.class,
        TestSample.class,
        TestTopologyChangeListener.class,
        TestTopologyDelete.class,
        TestTopologyDeleteSpecific.class,
        TestTinkerPopEvent.class,
        TestIo.class,
        TestComplex.class,
        TestTopologyDeleteSpecific.class,
        TestDeadLock.class,
        TestLabelLength.class,
        TestAddTemporaryVertex.class,
        TestBatchTemporaryVertex.class,
        TestIoEdge.class,
        TestBatchTemporaryVertex.class,
        TestUnoptimizedRepeatStep.class,
        TestTraversalFilterStepBarrier.class,
        TestOrStepBarrier.class,
        TestAndStepBarrier.class,
        TestNotStepBarrier.class,
        TestOrStep.class,
        TestAndStep.class,
        TestAndandOrStep.class,
        TestOrStepAfterVertexStepBarrier.class,
        TestDropStep.class,
        TestDropStepBarrier.class,
        TestDropStepTruncate.class,
        TestTopologyGraph.class,
        TestUnoptimizedRepeatStep.class,
        TestPropertyReference.class

})
public class AllTest {


}
