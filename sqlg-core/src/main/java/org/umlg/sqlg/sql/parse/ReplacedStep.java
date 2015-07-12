package org.umlg.sqlg.sql.parse;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.EdgeVertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.FlatMapStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.VertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.structure.*;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SchemaManager;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.util.SqlgUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Date: 2015/06/27
 * Time: 6:05 PM
 */
public class ReplacedStep<S, E> {

    private SchemaManager schemaManager;
    private FlatMapStep<S, E> step;
    private List<HasContainer> hasContainers;
    //This indicates the distanced of the replaced steps from the starting step. i.e. g.V(1).out().out().out() will be 0,1,2 for the 3 outs
    private int depth;

    public static <S, E> ReplacedStep from(SchemaManager schemaManager, FlatMapStep<S, E> flatMapStep, List<HasContainer> hasContainers) {
        ReplacedStep replacedStep = new ReplacedStep<>();
        replacedStep.step = flatMapStep;
        replacedStep.hasContainers = hasContainers;
        replacedStep.schemaManager = schemaManager;
        return replacedStep;
    }

    public FlatMapStep<S, E> getStep() {
        return step;
    }

    public List<HasContainer> getHasContainers() {
        return hasContainers;
    }


    public Set<SchemaTableTree> appendPath(SchemaTableTree schemaTableTree) {
        if (this.step instanceof VertexStep) {
            return appendPathForVertexStep(schemaTableTree);
        } else if (this.step instanceof EdgeVertexStep) {
            return appendPathForEdgeVertexStep(schemaTableTree);
        } else {
            throw new IllegalStateException("Only VertexStep and EdgeVertexStep is handled");
        }
    }

    private Set<SchemaTableTree> appendPathForEdgeVertexStep(SchemaTableTree schemaTableTree) {
        EdgeVertexStep edgeVertexStep = (EdgeVertexStep) this.step;
        return calculatePathFromEdgeToVertex(schemaTableTree, schemaTableTree.getSchemaTable(), edgeVertexStep.getDirection());
    }

    private Set<SchemaTableTree> appendPathForVertexStep(SchemaTableTree schemaTableTree) {
        Preconditions.checkArgument(schemaTableTree.getSchemaTable().isVertexTable());

        Set<SchemaTableTree> result = new HashSet<>();
        Pair<Set<SchemaTable>, Set<SchemaTable>> inAndOutLabelsFromCurrentPosition = this.schemaManager.getTableLabels(schemaTableTree.getSchemaTable());

        VertexStep vertexStep = (VertexStep) this.step;
        String[] edgeLabels = vertexStep.getEdgeLabels();
        Direction direction = vertexStep.getDirection();
        Class<? extends Element> elementClass = vertexStep.getReturnClass();

        Set<SchemaTable> inLabels = inAndOutLabelsFromCurrentPosition != null ? inAndOutLabelsFromCurrentPosition.getLeft() : new HashSet<>();
        Set<SchemaTable> outLabels = inAndOutLabelsFromCurrentPosition != null ? inAndOutLabelsFromCurrentPosition.getRight() : new HashSet<>();
        Set<SchemaTable> inLabelsToTraversers;
        Set<SchemaTable> outLabelsToTraversers;
        switch (vertexStep.getDirection()) {
            case IN:
                inLabelsToTraversers = filter(inLabels, edgeLabels);
                outLabelsToTraversers = new HashSet<>();
                break;
            case OUT:
                outLabelsToTraversers = filter(outLabels, edgeLabels);
                inLabelsToTraversers = new HashSet<>();
                break;
            case BOTH:
                inLabelsToTraversers = edgeLabels.length > 0 ? filter(inLabels, edgeLabels) : inLabels;
                outLabelsToTraversers = edgeLabels.length > 0 ? filter(outLabels, edgeLabels) : outLabels;
                break;
            default:
                throw new IllegalStateException("Unknown direction " + direction.name());
        }

        if (elementClass.isAssignableFrom(Edge.class)) {
            inLabelsToTraversers = filterEdgeOnIdHasContainers(inLabelsToTraversers);
            outLabelsToTraversers = filterEdgeOnIdHasContainers(outLabelsToTraversers);
        }

        //Each labelToTravers more than the first one forms a new distinct path
        for (SchemaTable inLabelsToTravers : inLabelsToTraversers) {
            if (elementClass.isAssignableFrom(Edge.class)) {
                SchemaTableTree schemaTableTreeChild = schemaTableTree.addChild(inLabelsToTravers, Direction.IN, elementClass, this.hasContainers, this.depth, this.step.getLabels());
                result.add(schemaTableTreeChild);
            } else {
                SchemaTableTree schemaTableTreeChild = schemaTableTree.addChild(inLabelsToTravers, Direction.IN, elementClass, this.hasContainers, this.depth, Collections.EMPTY_SET);
                result.addAll(calculatePathFromVertexToEdge(schemaTableTreeChild, inLabelsToTravers, Direction.IN));
            }
        }
        for (SchemaTable outLabelsToTravers : outLabelsToTraversers) {
            if (elementClass.isAssignableFrom(Edge.class)) {
                SchemaTableTree schemaTableTreeChild = schemaTableTree.addChild(outLabelsToTravers, Direction.OUT, elementClass, this.hasContainers, this.depth, this.step.getLabels());
                result.add(schemaTableTreeChild);
            } else {
                SchemaTableTree schemaTableTreeChild = schemaTableTree.addChild(outLabelsToTravers, Direction.OUT, elementClass, this.hasContainers, this.depth, Collections.EMPTY_SET);
                result.addAll(calculatePathFromVertexToEdge(schemaTableTreeChild, outLabelsToTravers, Direction.OUT));
            }
        }
        return result;
    }

    private Set<SchemaTable> filterEdgeOnIdHasContainers(Set<SchemaTable> inLabelsToTraversers) {
        Set<SchemaTable> idFilteredResult = new HashSet<>(inLabelsToTraversers);
        //Filter out labels if their is a hasContainer on the id field
        for (HasContainer idHasContainer : getIdHasContainer()) {
            SchemaTable hasContainerSchemaTable = RecordId.from(idHasContainer.getValue().toString()).getSchemaTable();
            hasContainerSchemaTable = SchemaTable.of(hasContainerSchemaTable.getSchema(), SchemaManager.EDGE_PREFIX + hasContainerSchemaTable.getTable());
            for (SchemaTable schemaTable : inLabelsToTraversers) {
                if (!schemaTable.equals(hasContainerSchemaTable)) {
                    idFilteredResult.remove(schemaTable);
                }
            }
        }
        return idFilteredResult;
    }

    private Set<SchemaTableTree> filterVertexOnIdHasContainers(Set<SchemaTableTree> inLabelsToTraversers) {
        Set<SchemaTableTree> idFilteredResult = new HashSet<>(inLabelsToTraversers);
        //Filter out labels if their is a hasContainer on the id field
        for (HasContainer idHasContainer : getIdHasContainer()) {
            SchemaTable hasContainerSchemaTable = RecordId.from(idHasContainer.getValue().toString()).getSchemaTable();
            hasContainerSchemaTable = SchemaTable.of(hasContainerSchemaTable.getSchema(), SchemaManager.VERTEX_PREFIX + hasContainerSchemaTable.getTable());
            for (SchemaTableTree schemaTableTree : inLabelsToTraversers) {
                if (!schemaTableTree.getSchemaTable().equals(hasContainerSchemaTable)) {
                    idFilteredResult.remove(schemaTableTree);
                }
            }
        }
        return idFilteredResult;
    }

    private Set<SchemaTableTree> calculatePathFromEdgeToVertex(SchemaTableTree schemaTableTree, SchemaTable labelToTravers, Direction direction) {
        Preconditions.checkArgument(!labelToTravers.isVertexTable());
        Set<SchemaTableTree> result = new HashSet<>();
        Map<String, Set<String>> edgeForeignKeys = this.schemaManager.getAllEdgeForeignKeys();
        //join from the edge table to the incoming vertex table
        Set<String> foreignKeys = edgeForeignKeys.get(labelToTravers.toString());
        //Every foreignKey for the given direction must be joined on
        for (String foreignKey : foreignKeys) {
            String foreignKeySchema = foreignKey.split("\\.")[0];
            String foreignKeyTable = foreignKey.split("\\.")[1];
            if ((direction == Direction.BOTH || direction == Direction.OUT) && foreignKey.endsWith(SchemaManager.OUT_VERTEX_COLUMN_END)) {
                SchemaTableTree schemaTableTreeChild = schemaTableTree.addChild(
                        SchemaTable.of(foreignKeySchema, SchemaManager.VERTEX_PREFIX + SqlgUtil.removeTrailingOutId(foreignKeyTable)),
                        Direction.OUT,
                        Vertex.class,
                        this.hasContainers,
                        this.depth,
                        true,
                        this.step.getLabels()
                );
                result.add(schemaTableTreeChild);
            }
            if ((direction == Direction.BOTH || direction == Direction.IN) && foreignKey.endsWith(SchemaManager.IN_VERTEX_COLUMN_END)) {
                SchemaTableTree schemaTableTreeChild = schemaTableTree.addChild(
                        SchemaTable.of(foreignKeySchema, SchemaManager.VERTEX_PREFIX + SqlgUtil.removeTrailingInId(foreignKeyTable)),
                        Direction.IN,
                        Vertex.class,
                        this.hasContainers,
                        this.depth,
                        true,
                        this.step.getLabels()
                );
                result.add(schemaTableTreeChild);
            }
        }
        return result;
    }

    private Set<SchemaTableTree> calculatePathFromVertexToEdge(SchemaTableTree schemaTableTree, SchemaTable labelToTravers, Direction direction) {
        Preconditions.checkArgument(!labelToTravers.isVertexTable());
        Set<SchemaTableTree> result = new HashSet<>();
        Map<String, Set<String>> edgeForeignKeys = this.schemaManager.getAllEdgeForeignKeys();
        //join from the edge table to the incoming vertex table
        Set<String> foreignKeys = edgeForeignKeys.get(labelToTravers.toString());
        //Every foreignKey for the given direction must be joined on
        for (String foreignKey : foreignKeys) {
            String foreignKeySchema = foreignKey.split("\\.")[0];
            String foreignKeyTable = foreignKey.split("\\.")[1];
            if (direction == Direction.IN && foreignKey.endsWith(SchemaManager.OUT_VERTEX_COLUMN_END)) {
                SchemaTableTree schemaTableTree1 = schemaTableTree.addChild(
                        SchemaTable.of(foreignKeySchema, SchemaManager.VERTEX_PREFIX + SqlgUtil.removeTrailingOutId(foreignKeyTable)),
                        direction,
                        Vertex.class,
                        this.hasContainers,
                        this.depth,
                        this.step.getLabels()
                );
                result.add(schemaTableTree1);
            } else if (direction == Direction.OUT && foreignKey.endsWith(SchemaManager.IN_VERTEX_COLUMN_END)) {
                SchemaTableTree schemaTableTree1 = schemaTableTree.addChild(
                        SchemaTable.of(foreignKeySchema, SchemaManager.VERTEX_PREFIX + SqlgUtil.removeTrailingInId(foreignKeyTable)),
                        direction,
                        Vertex.class,
                        this.hasContainers,
                        this.depth,
                        this.step.getLabels()
                );
                result.add(schemaTableTree1);
            }
        }
        Set<SchemaTableTree> filteredResult = filterVertexOnIdHasContainers(result);
        return filteredResult;
    }

    public Set<SchemaTableTree> calculatePathForStep(Set<SchemaTableTree> schemaTableTrees) {
        Set<SchemaTableTree> result = new HashSet<>();
        for (SchemaTableTree schemaTableTree : schemaTableTrees) {
            result.addAll(this.appendPath(schemaTableTree));
        }
        return result;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    private Set<SchemaTable> filter(Set<SchemaTable> labels, String[] edgeLabels) {
        Set<SchemaTable> result = new HashSet<>();
        List<String> edges = Arrays.asList(edgeLabels);
        for (SchemaTable label : labels) {
            if (!label.getTable().startsWith(SchemaManager.EDGE_PREFIX)) {
                throw new IllegalStateException("Expected label to start with " + SchemaManager.EDGE_PREFIX);
            }
            String rawLabel = label.getTable().substring(SchemaManager.EDGE_PREFIX.length());
            //only filter if there are edges to filter
            if (!edges.isEmpty()) {
                if (edges.contains(rawLabel)) {
                    result.add(label);
                }
            } else {
                result.add(label);
            }
        }
        return result;
    }

    private List<HasContainer> getIdHasContainer() {
        return this.hasContainers.stream().filter(h -> h.getKey().equals(T.id.getAccessor())).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return this.step.toString() + " :: " + this.hasContainers.toString();
    }
}
