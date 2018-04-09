/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.segment.file.proc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.apache.commons.io.input.NullInputStream;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.segment.file.proc.Proc.Backend;
import org.apache.jackrabbit.oak.segment.file.proc.Proc.Backend.Commit;
import org.apache.jackrabbit.oak.segment.file.proc.Proc.Backend.Record;
import org.apache.jackrabbit.oak.segment.file.proc.Proc.Backend.Segment;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Test;

public class ProcTest {

    @Test
    public void procNodeShouldExposeStore() {
        assertTrue(
            Proc.of(mock(Backend.class))
                .hasChildNode("store")
        );
    }

    @Test
    public void storeNodeShouldExist() {
        assertTrue(
            Proc.of(mock(Backend.class))
                .getChildNode("store")
                .exists()
        );
    }

    @Test
    public void storeNodeShouldExposeAllTarNames() {
        Set<String> names = Sets.newHashSet("t1", "t2", "t3");
        Backend backend = mock(Backend.class);
        when(backend.getTarNames()).thenReturn(names);
        assertEquals(names, Sets.newHashSet(
            Proc.of(backend)
                .getChildNode("store")
                .getChildNodeNames()
        ));
    }

    @Test
    public void storeNodeShouldExposeTarName() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        assertTrue(
            Proc.of(backend)
                .getChildNode("store")
                .hasChildNode("t")
        );
    }

    @Test
    public void tarNodeShouldExist() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        assertTrue(
            Proc.of(backend)
                .getChildNode("store")
                .getChildNode("t")
                .exists()
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void storeNodeShouldNotBeBuildable() {
        Proc.of(mock(Backend.class))
            .getChildNode("store")
            .builder();
    }

    @Test
    public void tarNodeShouldExposeSegmentId() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        assertTrue(
            Proc.of(backend)
                .getChildNode("store")
                .getChildNode("t")
                .hasChildNode("s")
        );
    }

    @Test
    public void tarNodeShouldExposeAllSegmentIds() {
        Set<String> names = Sets.newHashSet("s1", "s2", "s3");
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.getSegmentIds("t")).thenReturn(names);
        assertEquals(names, Sets.newHashSet(
            Proc.of(backend)
                .getChildNode("store")
                .getChildNode("t")
                .getChildNodeNames()
        ));
    }

    @Test
    public void segmentNodeShouldExist() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        assertTrue(
            Proc.of(backend)
                .getChildNode("store")
                .getChildNode("t")
                .getChildNode("s")
                .exists()
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void tarNodeShouldNotBeBuildable() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .builder();
    }

    @Test
    public void tarNodeShouldHaveNameProperty() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.getTarSize("t")).thenReturn(Optional.empty());

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getProperty("name");

        assertEquals(Type.STRING, property.getType());;
        assertEquals("t", property.getValue(Type.STRING));
    }

    @Test
    public void tarNodeShouldHaveSizeProperty() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.getTarSize("t")).thenReturn(Optional.of(1L));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getProperty("size");

        assertEquals(Type.LONG, property.getType());;
        assertEquals(1L, property.getValue(Type.LONG).longValue());
    }

    @Test
    public void segmentNodeShouldHaveGenerationProperty() {
        Segment segment = mock(Segment.class);
        when(segment.getGeneration()).thenReturn(1);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("generation");

        assertEquals(Type.LONG, property.getType());
        assertEquals(1, property.getValue(Type.LONG).intValue());
    }

    @Test
    public void segmentNodeShouldHaveFullGenerationProperty() {
        Segment segment = mock(Segment.class);
        when(segment.getFullGeneration()).thenReturn(1);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("fullGeneration");

        assertEquals(Type.LONG, property.getType());
        assertEquals(1, property.getValue(Type.LONG).intValue());
    }

    @Test
    public void segmentNodeShouldHaveCompactedProperty() {
        Segment segment = mock(Segment.class);
        when(segment.isCompacted()).thenReturn(true);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("compacted");

        assertEquals(Type.BOOLEAN, property.getType());
        assertTrue(property.getValue(Type.BOOLEAN));
    }

    @Test
    public void segmentNodeShouldHaveLengthProperty() {
        Segment segment = mock(Segment.class);
        when(segment.getLength()).thenReturn(1);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("length");

        assertEquals(Type.LONG, property.getType());
        assertEquals(1, property.getValue(Type.LONG).intValue());
    }

    @Test
    public void segmentNodeShouldHaveDataProperty() {
        InputStream stream = new NullInputStream(1);

        Segment segment = mock(Segment.class);
        when(segment.getLength()).thenReturn(1);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));
        when(backend.getSegmentData("s")).thenReturn(Optional.of(stream));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("data");

        assertEquals(Type.BINARY, property.getType());
        assertSame(stream, property.getValue(Type.BINARY).getNewStream());
        assertEquals(1, property.getValue(Type.BINARY).length());
    }

    @Test
    public void segmentNodeShouldHaveIdProperty() {
        Segment segment = mock(Segment.class);
        when(segment.getLength()).thenReturn(1);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("id");

        assertEquals(Type.STRING, property.getType());
        assertEquals("s", property.getValue(Type.STRING));
    }

    @Test
    public void segmentNodeShouldHaveVersionProperty() {
        Segment segment = mock(Segment.class);
        when(segment.getVersion()).thenReturn(1);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("version");

        assertEquals(Type.LONG, property.getType());
        assertEquals(1, property.getValue(Type.LONG).longValue());
    }

    @Test
    public void segmentNodeShouldHaveIsDataSegmentProperty() {
        Segment segment = mock(Segment.class);
        when(segment.isDataSegment()).thenReturn(true);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("isDataSegment");

        assertEquals(Type.BOOLEAN, property.getType());
        assertTrue(property.getValue(Type.BOOLEAN));
    }

    @Test
    public void segmentNodeShouldHaveInfoProperty() {
        Segment segment = mock(Segment.class);
        when(segment.getInfo()).thenReturn(Optional.of("info"));

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegment("s")).thenReturn(Optional.of(segment));

        PropertyState property = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getProperty("info");

        assertEquals(Type.STRING, property.getType());
        assertEquals("info", property.getValue(Type.STRING));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void segmentNodeShouldNotBeBuildable() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);

        Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .builder();
    }

    @Test
    public void segmentNodeShouldExposeReferences() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);

        NodeState segment = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s");

        assertTrue(segment.hasChildNode("references"));
        assertNotNull(segment.getChildNode("references"));
        assertTrue(segment.getChildNode("references").exists());
    }

    @Test
    public void segmentReferencesNodeShouldExposeReference() {
        List<String> references = Collections.singletonList("u");

        Segment segment = mock(Segment.class);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentReferences("s")).thenReturn(Optional.of(references));
        when(backend.getSegment("u")).thenReturn(Optional.of(segment));

        NodeState r = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("references")
            .getChildNode("0");

        assertEquals("u", r.getProperty("id").getValue(Type.STRING));
    }

    @Test
    public void segmentReferencesNodeShouldExposeAllReference() {
        List<String> references = Arrays.asList(
            "u", "v", "w"
        );

        Segment segment = mock(Segment.class);
        when(segment.getInfo()).thenReturn(Optional.empty());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentReferences("s")).thenReturn(Optional.of(references));
        when(backend.getSegment(any())).thenReturn(Optional.of(segment));

        NodeState rr = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("references");

        for (int i = 0; i < references.size(); i++) {
            NodeState r = rr.getChildNode(Integer.toString(i));
            assertEquals(references.get(i), r.getProperty("id").getValue(Type.STRING));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void segmentReferencesNodeShouldNotBeBuildable() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);

        Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("references")
            .builder();
    }

    @Test
    public void segmentNodeShouldExposeRecordsNode() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);

        NodeState n = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records");

        assertTrue(n.exists());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void recordsNodeShouldNotBeBuildable() {
        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);

        Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records")
            .builder();
    }

    @Test
    public void recordsNodeShouldExposeRecordNumber() {
        Record record = mock(Record.class);
        when(record.getNumber()).thenReturn(1);

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentRecords("s")).thenReturn(Optional.of(Collections.singletonList(record)));

        assertTrue(
            Proc.of(backend)
                .getChildNode("store")
                .getChildNode("t")
                .getChildNode("s")
                .getChildNode("records")
                .hasChildNode("1")
        );
    }

    @Test
    public void recordsNodeShouldExposeAllRecordNumbers() {
        Set<Integer> numbers = Sets.newHashSet(1, 2, 3);

        Set<Record> records = numbers.stream()
            .map(n -> {
                Record record = mock(Record.class);
                when(record.getNumber()).thenReturn(n);
                return record;
            })
            .collect(Collectors.toSet());

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentRecords("s")).thenReturn(Optional.of(records));

        NodeState n = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records");

        Set<String> names = numbers.stream()
            .map(x -> Integer.toString(x))
            .collect(Collectors.toSet());

        assertEquals(names, Sets.newHashSet(n.getChildNodeNames()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void recordNodeShouldNotBeBuildable() {
        Record record = mock(Record.class);
        when(record.getNumber()).thenReturn(1);

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentRecords("s")).thenReturn(Optional.of(Collections.singletonList(record)));

        Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records")
            .getChildNode("1")
            .builder();
    }

    @Test
    public void recordNodeShouldHaveNumberProperty() {
        Record record = mock(Record.class);
        when(record.getNumber()).thenReturn(1);
        when(record.getType()).thenReturn("t");

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentRecords("s")).thenReturn(Optional.of(Collections.singletonList(record)));

        PropertyState p = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records")
            .getChildNode("1")
            .getProperty("number");

        assertEquals(Type.LONG, p.getType());
        assertEquals(1, p.getValue(Type.LONG).intValue());
    }

    @Test
    public void recordNodeShouldHaveOffsetProperty() {
        Record record = mock(Record.class);
        when(record.getNumber()).thenReturn(1);
        when(record.getOffset()).thenReturn(2);
        when(record.getType()).thenReturn("t");

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentRecords("s")).thenReturn(Optional.of(Collections.singletonList(record)));

        PropertyState p = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records")
            .getChildNode("1")
            .getProperty("offset");

        assertEquals(Type.LONG, p.getType());
        assertEquals(2, p.getValue(Type.LONG).intValue());
    }

    @Test
    public void recordNodeShouldHaveTypeProperty() {
        Record record = mock(Record.class);
        when(record.getNumber()).thenReturn(1);
        when(record.getType()).thenReturn("t");

        Backend backend = mock(Backend.class);
        when(backend.tarExists("t")).thenReturn(true);
        when(backend.segmentExists("t", "s")).thenReturn(true);
        when(backend.getSegmentRecords("s")).thenReturn(Optional.of(Collections.singletonList(record)));

        PropertyState p = Proc.of(backend)
            .getChildNode("store")
            .getChildNode("t")
            .getChildNode("s")
            .getChildNode("records")
            .getChildNode("1")
            .getProperty("type");

        assertEquals(Type.STRING, p.getType());
        assertEquals("t", p.getValue(Type.STRING));
    }

    @Test
    public void procShouldExposeJournal() {
        assertTrue(
            Proc.of(mock(Backend.class))
                .hasChildNode("journal")
        );
    }

    @Test
    public void journalNodeShouldExposeCommitHandle() {
        Backend backend = mock(Backend.class);
        when(backend.commitExists("h")).thenReturn(true);

        assertTrue(
            Proc.of(backend)
                .getChildNode("journal")
                .hasChildNode("h")
        );
    }

    @Test
    public void journalNodeShouldExposeAllCommitHandles() {
        Set<String> names = Sets.newHashSet("h1", "h2", "h3");

        Backend backend = mock(Backend.class);
        when(backend.getCommitHandles()).thenReturn(names);

        assertEquals(names, Sets.newHashSet(
            Proc.of(backend)
                .getChildNode("journal")
                .getChildNodeNames()
        ));
    }

    @Test
    public void commitNodeShouldExist() {
        Backend backend = mock(Backend.class);
        when(backend.commitExists("h")).thenReturn(true);

        assertTrue(
            Proc.of(backend)
                .getChildNode("journal")
                .getChildNode("h")
                .exists()
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void journalNodeShouldNotBeBuildable() {
        Proc.of(mock(Backend.class))
            .getChildNode("journal")
            .builder();
    }

    @Test
    public void commitNodeShouldHaveTimestampProperty() {
        Commit commit = mock(Commit.class);
        when(commit.getTimestamp()).thenReturn(1L);
        when(commit.getRevision()).thenReturn("");

        Backend backend = mock(Backend.class);
        when(backend.commitExists("h")).thenReturn(true);
        when(backend.getCommit("h")).thenReturn(Optional.of(commit));

        PropertyState property = Proc.of(backend)
            .getChildNode("journal")
            .getChildNode("h")
            .getProperty("timestamp");

        assertEquals(Type.LONG, property.getType());
        assertEquals(1L, property.getValue(Type.LONG).longValue());
    }

    @Test
    public void commitNodeShouldExposeRoot() {
        Commit commit = mock(Commit.class);
        when(commit.getRoot()).thenReturn(Optional.of(EmptyNodeState.EMPTY_NODE));

        Backend backend = mock(Backend.class);
        when(backend.commitExists("h")).thenReturn(true);
        when(backend.getCommit("h")).thenReturn(Optional.of(commit));

        NodeState commitNode = Proc.of(backend)
            .getChildNode("journal")
            .getChildNode("h");

        assertTrue(commitNode.hasChildNode("root"));
        assertTrue(Sets.newHashSet(commitNode.getChildNodeNames()).contains("root"));
    }

    @Test
    public void rootNodeShouldExist() {
        NodeBuilder builder = EmptyNodeState.EMPTY_NODE.builder();
        builder.setProperty("root", true);
        NodeState root = builder.getNodeState();

        Commit commit = mock(Commit.class);
        when(commit.getRoot()).thenReturn(Optional.of(root));

        Backend backend = mock(Backend.class);
        when(backend.commitExists("h")).thenReturn(true);
        when(backend.getCommit("h")).thenReturn(Optional.of(commit));

        assertSame(root,
            Proc.of(backend)
                .getChildNode("journal")
                .getChildNode("h")
                .getChildNode("root")
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void commitNodeShouldNotBeBuildable() {
        Commit commit = mock(Commit.class);
        when(commit.getRoot()).thenReturn(Optional.of(EmptyNodeState.EMPTY_NODE));

        Backend backend = mock(Backend.class);
        when(backend.commitExists("h")).thenReturn(true);
        when(backend.getCommit("h")).thenReturn(Optional.of(commit));

        Proc.of(backend)
            .getChildNode("journal")
            .getChildNode("h")
            .builder();
    }

}
