package org.umlg.sqlg.test.io;

import org.apache.tinkerpop.gremlin.TestHelper;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.junit.Assert;
import org.junit.Test;
import org.umlg.sqlg.test.BaseTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 *         Date: 2017/06/13
 */
public class TestIo extends BaseTest {

    public String ioType;

    public boolean assertViaDirectEquality;

    public boolean assertEdgesAtSameTimeAsVertex;

    public Function<Graph, GraphReader> readerMaker;

    public Function<Graph, GraphWriter> writerMaker;

    @Test
    public void shouldReadWriteVertexWithBOTHEdges() throws Exception {

        this.ioType = "graphson-v1-embedded";
        this.assertViaDirectEquality = true;
        this.assertEdgesAtSameTimeAsVertex = false;
        this.readerMaker = g -> g.io(IoCore.graphson()).reader().mapper(g.io(IoCore.graphson()).mapper().create()).create();
        this.writerMaker = g -> g.io(IoCore.graphson()).writer().mapper(g.io(IoCore.graphson()).mapper().create()).create();

        Graph graph = this.sqlgGraph;
        final Vertex v1 = graph.addVertex("name", "marko", T.label, "person");

        final Vertex v2 = graph.addVertex(T.label, "person");
        final Edge e1 = v2.addEdge("friends", v1, "weight", 0.5d);
        final Edge e2 = v1.addEdge("friends", v2, "weight", 1.0d);

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final GraphWriter writer = writerMaker.apply(graph);
            writer.writeVertex(os, v1, Direction.BOTH);

            final AtomicBoolean calledVertex = new AtomicBoolean(false);
            final AtomicBoolean calledEdge1 = new AtomicBoolean(false);
            final AtomicBoolean calledEdge2 = new AtomicBoolean(false);

            final GraphReader reader = readerMaker.apply(graph);
            try (final ByteArrayInputStream bais = new ByteArrayInputStream(os.toByteArray())) {
                reader.readVertex(bais, attachable -> {
                    final Vertex detachedVertex = attachable.get();
                    if (assertViaDirectEquality) {
                        TestHelper.validateVertexEquality(v1, detachedVertex, assertEdgesAtSameTimeAsVertex);
                    } else {
                        Assert.assertEquals(v1.id(), graph.vertices(detachedVertex.id().toString()).next().id());
                        Assert.assertEquals(v1.label(), detachedVertex.label());
                        Assert.assertEquals(1, IteratorUtils.count(detachedVertex.properties()));
                        Assert.assertEquals("marko", detachedVertex.value("name"));
                    }
                    calledVertex.set(true);
                    return detachedVertex;
                }, attachable -> {
                    final Edge detachedEdge = attachable.get();
                    final Predicate<Edge> matcher = assertViaDirectEquality ? e -> detachedEdge.id().equals(e.id()) :
                            e -> graph.edges(detachedEdge.id().toString()).next().id().equals(e.id());
                    if (matcher.test(e1)) {
                        if (assertViaDirectEquality) {
                            TestHelper.validateEdgeEquality(e1, detachedEdge);
                        } else {
                            Assert.assertEquals(e1.id(), graph.edges(detachedEdge.id().toString()).next().id());
                            Assert.assertEquals(v1.id(), graph.vertices(detachedEdge.inVertex().id().toString()).next().id());
                            Assert.assertEquals(v2.id(), graph.vertices(detachedEdge.outVertex().id().toString()).next().id());
                            Assert.assertEquals(v2.label(), detachedEdge.inVertex().label());
                            Assert.assertEquals(e1.label(), detachedEdge.label());
                            Assert.assertEquals(1, IteratorUtils.count(detachedEdge.properties()));
                            Assert.assertEquals(0.5d, detachedEdge.value("weight"), 0.000001d);
                        }
                        calledEdge1.set(true);
                    } else if (matcher.test(e2)) {
                        if (assertViaDirectEquality) {
                            TestHelper.validateEdgeEquality(e2, detachedEdge);
                        } else {
                            Assert.assertEquals(e2.id(), graph.edges(detachedEdge.id().toString()).next().id());
                            Assert.assertEquals(v2.id(), graph.vertices(detachedEdge.inVertex().id().toString()).next().id());
                            Assert.assertEquals(v1.id(), graph.vertices(detachedEdge.outVertex().id().toString()).next().id());
                            Assert.assertEquals(v1.label(), detachedEdge.outVertex().label());
                            Assert.assertEquals(e2.label(), detachedEdge.label());
                            Assert.assertEquals(1, IteratorUtils.count(detachedEdge.properties()));
                            Assert.assertEquals(1.0d, detachedEdge.value("weight"), 0.000001d);
                        }
                        calledEdge2.set(true);
                    } else {
                        Assert.fail("An edge id generated that does not exist");
                    }

                    return null;
                }, Direction.BOTH);
            }

            Assert.assertTrue(calledVertex.get());
            Assert.assertTrue(calledEdge1.get());
            Assert.assertTrue(calledEdge2.get());
        }
    }
}
