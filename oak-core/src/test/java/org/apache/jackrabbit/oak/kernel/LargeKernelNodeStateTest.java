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
package org.apache.jackrabbit.oak.kernel;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LargeKernelNodeStateTest extends AbstractKernelTest {

    private static final int N = KernelNodeState.MAX_CHILD_NAMES;

    private NodeState state;

    @Before
    public void setUp() throws CommitFailedException {
        NodeStore store = createNodeStore();

        NodeBuilder builder = store.getRoot().builder();
        builder.setProperty("a", 1);
        for (int i = 0; i <= N; i++) {
            builder.child("x" + i);
        }

        state = store.merge(builder, EditorProvider.EMPTY, CommitInfo.EMPTY);
    }

    @After
    public void tearDown() {
        state = null;
    }

    @Test
    public void testGetChildNodeCount() {
        assertEquals(N + 1, state.getChildNodeCount(N + 2));
    }

    @Test
    public void testGetChildNode() {
        assertTrue(state.getChildNode("x0").exists());
        assertTrue(state.getChildNode("x1").exists());
        assertTrue(state.getChildNode("x" + N).exists());
        assertFalse(state.getChildNode("x" + (N + 1)).exists());
    }

    @Test
    public void testGetChildNodeEntries() {
        long count = 0;
        for (ChildNodeEntry entry : state.getChildNodeEntries()) {
            count++;
        }
        assertEquals(N + 1, count);
    }

}
