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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MemoryChildNodeEntry;
import org.apache.jackrabbit.oak.segment.file.proc.Proc.Backend;
import org.apache.jackrabbit.oak.spi.state.AbstractNodeState;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

class SegmentReferencesNode extends AbstractNodeState {

    private final Backend backend;

    private final String segmentId;

    SegmentReferencesNode(Backend backend, String segmentId) {
        this.backend = backend;
        this.segmentId = segmentId;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Nonnull
    @Override
    public Iterable<? extends PropertyState> getProperties() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasChildNode(@Nonnull String name) {
        return NodeUtils.hasChildNode(getChildNodeEntries(), name);
    }

    @Nonnull
    @Override
    public NodeState getChildNode(@Nonnull String name) throws IllegalArgumentException {
        return NodeUtils.getChildNode(getChildNodeEntries(), name);
    }

    @Nonnull
    @Override
    public Iterable<? extends ChildNodeEntry> getChildNodeEntries() {
        return backend.getSegmentReferences(segmentId)
            .map(this::getChildNodeEntries)
            .orElse(Collections.emptyList());
    }

    private Iterable<ChildNodeEntry> getChildNodeEntries(Iterable<String> references) {
        int i = 0;

        List<ChildNodeEntry> entries = new ArrayList<>();

        for (String reference : references) {
            entries.add(new MemoryChildNodeEntry(Integer.toString(i++), new SegmentNode(backend, reference)));
        }

        return entries;
    }

    @Nonnull
    @Override
    public NodeBuilder builder() {
        throw new UnsupportedOperationException();
    }

}
