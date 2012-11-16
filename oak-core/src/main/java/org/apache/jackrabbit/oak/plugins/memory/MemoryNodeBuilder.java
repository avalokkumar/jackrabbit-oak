/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.memory;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

import com.google.common.collect.Maps;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.AbstractNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStateDiff;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.jackrabbit.oak.plugins.memory.ModifiedNodeState.with;
import static org.apache.jackrabbit.oak.plugins.memory.ModifiedNodeState.withNodes;
import static org.apache.jackrabbit.oak.plugins.memory.ModifiedNodeState.withProperties;

/**
 * In-memory node state builder.
 * <p>
 * TODO: The following description is somewhat out of date
 * <p>
 * The following two builder states are used
 * to properly track uncommitted chances without relying on weak references
 * or requiring hard references on the entire accessed subtree:
 * <dl>
 *   <dt>unmodified</dt>
 *   <dd>
 *     A child builder with no content changes starts in this state.
 *     It keeps a reference to the parent builder and knows it's name for
 *     use when connecting. Before each access the unconnected builder
 *     checks the parent for relevant changes to connect to. As long as
 *     there are no such changes, the builder remains unconnected and
 *     uses the immutable base state to respond to any content accesses.
 *   </dd>
 *   <dt>connected</dt>
 *   <dd>
 *     Once a child node is first modified, it switches it's internal
 *     state from the immutable base state to a mutable one and records
 *     a hard reference to that state in the mutable parent state. After
 *     that the parent reference is cleared and no more state checks are
 *     made. Any other builder instances that refer to the same child node
 *     will update their internal states to point to that same mutable
 *     state instance and thus become connected at next access.
 *     A root state builder is always connected.
 *   </dd>
 * </dl>
 */
public class MemoryNodeBuilder implements NodeBuilder {

    /**
     * Parent builder, or {@code null} for a root builder.
     */
    private final MemoryNodeBuilder parent;

    /**
     * Name of this child node within the parent builder,
     * or {@code null} for a root builder.
     */
    private final String name;

    /**
     * Root builder, or {@code this} for the root itself.
     */
    private final MemoryNodeBuilder root;

    /**
     * Internal revision counter that is incremented in the root builder
     * whenever anything changes in the tree below. Each builder instance
     * has its own copy of the revision counter, for quickly checking whether
     * any state changes are needed.
     */
    private long revision;

    /**
     * The base state of this builder, or {@code null} if this builder
     * represents a new node that didn't yet exist in the base content tree.
     */
    private NodeState baseState;

    /**
     * The shared mutable state of connected builder instances, or
     * {@code null} until this builder has been connected.
     */
    private MutableNodeState writeState;

    /**
     * Creates a new in-memory node state builder.
     *
     * @param parent parent node state builder
     * @param name name of this node
     */
    protected MemoryNodeBuilder(MemoryNodeBuilder parent, String name) {
        this.parent = checkNotNull(parent);
        this.name = checkNotNull(name);

        this.root = parent.root;
        this.revision = -1;

        this.baseState = null;
        this.writeState = null;
    }

    /**
     * Creates a new in-memory node state builder.
     *
     * @param base base state of the new builder
     */
    public MemoryNodeBuilder(@Nonnull NodeState base) {
        this.parent = null;
        this.name = null;

        this.root = this;
        this.revision = 0;

        this.baseState = checkNotNull(base);
        this.writeState = new MutableNodeState(baseState);
    }

    private boolean classInvariants() {
        boolean rootHasNoParent = isRoot() == (parent == null);
        boolean rootHasWriteState = root.writeState != null;

        return rootHasNoParent && rootHasWriteState;
    }

    private boolean isRoot() {
        return this == root;
    }

    /**
     * Return the base state of the named child. Assumes {@code read()} needs not be called.
     * @param name  name of the child
     * @return  base state of the child
     */
    private NodeState getBaseState(String name) {
        if (baseState != null) {
            return baseState.getChildNode(name);
        } else {
            return null;
        }
    }

    /**
     * Determine whether the base state has a child of the given name.
     * Assumes {@code read()} needs not be called.
     * @param name  name of the child
     * @return  {@code true} iff the base state has a child of the given name.
     */
    private boolean hasBaseState(String name) {
        return baseState != null && baseState.hasChildNode(name);
    }

    /**
     * Return the write state of the named child. Assumes {@code read()}, {@code write()} needs not be called.
     * @param name  name of the child
     * @return  base state of the child
     */
    private MutableNodeState getWriteState(String name) {
        if (writeState != null) {
            return writeState.nodes.get(name);
        }
        else {
            return null;
        }
    }

    /**
     * Determine whether the named child has been removed. This is the
     * case when the write state has a corresponding {@code null} entry.
     * Assumes {@code read()}, {@code write()} needs not be called.
     * @param name  name of the child
     * @return  {@code true} iff a child with the given name has been removed
     */
    private boolean removed(String name) {
        return writeState != null && writeState.isRemoved(name);
    }

    @Nonnull
    private NodeState read() {
        if (revision != root.revision) {
            assert(!isRoot()); // root never gets here
            parent.read();

            // The builder could have been reset, need to re-get base state
            baseState = parent.getBaseState(name);

            // ... same for the write state
            writeState = parent.getWriteState(name);
            if (writeState == null) {
                checkState(!parent.removed(name), "This node has been removed");
            }

            revision = root.revision;
        }
        if (writeState != null) {
            return writeState;
        } else {
            // TODO: double check whether this assertion holds for all cases
            assert baseState != null;
            return baseState;
        }
    }

    @Nonnull
    private MutableNodeState write() {
        return write(root.revision + 1);
    }

    @Nonnull
    private MutableNodeState write(long newRevision) {
        // make sure that all revision numbers up to the root gets updated
        if (!isRoot()) {
            parent.write(newRevision);
        }

        if (writeState == null || revision != root.revision) {
            assert(!isRoot()); // root never gets here since writeState of root is never null

            // The builder could have been reset, need to re-get base state
            baseState = parent.getBaseState(name);

            assert parent.writeState != null; // we just called parent.write()
            writeState = parent.getWriteState(name);
            if (writeState == null) {
                checkState(!parent.removed(name), "This node has been removed");
                writeState = new MutableNodeState(baseState);
                parent.writeState.nodes.put(name, writeState);
            }
        }

        revision = newRevision;
        assert writeState != null;
        return writeState;
    }

    /**
     * Factory method for creating new child state builders. Subclasses may
     * override this method to control the behavior of child state builders.
     *
     * @return new builder
     */
    protected MemoryNodeBuilder createChildBuilder(String name) {
        return new MemoryNodeBuilder(this, name);
    }

    /**
     * Called whenever <em>this</em> node is modified, i.e. a property is
     * added, changed or removed, or a child node is added or removed. Changes
     * inside child nodes or the subtrees below are not reported. The default
     * implementation does nothing, but subclasses may override this method
     * to better track changes.
     */
    protected void updated() {
        // do nothing
    }

    protected void compareAgainstBaseState(NodeStateDiff diff) {
        NodeState state = read();
        if (writeState != null) {
            writeState.compareAgainstBaseState(state, diff);
        }
    }

    //--------------------------------------------------------< NodeBuilder >---

    @Override
    public boolean isNew() {
        assert classInvariants();
        return !isRoot() && parent.isNew(name);
    }

    private boolean isNew(String name) {
        read();
        return !hasBaseState(name) && hasChildNode(name);
    }

    @Override
    public boolean isRemoved() {
        assert classInvariants();
        return !isRoot() && (parent.isRemoved() || parent.isRemoved(name));
    }

    private boolean isRemoved(String name) {
        read();
        return hasBaseState(name) && !hasChildNode(name);
    }

    @Override
    public boolean isModified() {
        assert classInvariants();
        read();
        if (writeState == null) {
            return false;
        }
        else {
            for (Entry<String, MutableNodeState> n : writeState.nodes.entrySet()) {
                if (n.getValue() == null) {
                    return true;
                }
                if (!(hasBaseState(n.getKey()))) {
                    return true;
                }
            }
            for (Entry<String, PropertyState> p : writeState.properties.entrySet()) {
                PropertyState pState = p.getValue();
                if (pState == null) {
                    return true;
                }
                if (baseState == null || !pState.equals(baseState.getProperty(p.getKey()))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public NodeState getNodeState() {
        assert classInvariants();
        read();
        if (writeState != null) {
            return writeState.snapshot();
        } else {
            // TODO: double check whether this assertion holds for all cases
            assert baseState != null;
            return baseState;
        }
    }

    @Override
    public NodeState getBaseState() {
        assert classInvariants();
        read();
        return baseState;
    }

    @Override
    public void reset(NodeState newBase) {
        checkState(isRoot(), "Cannot reset a non-root builder");
        assert classInvariants();
        baseState = checkNotNull(newBase);
        writeState = new MutableNodeState(baseState);
        revision++;
    }

    @Override
    public long getChildNodeCount() {
        assert classInvariants();
        return read().getChildNodeCount();
    }

    @Override
    public boolean hasChildNode(String name) {
        assert classInvariants();
        return read().hasChildNode(name);
    }

    @Override
    public Iterable<String> getChildNodeNames() {
        assert classInvariants();
        return read().getChildNodeNames();
    }

    @Override @Nonnull
    public NodeBuilder setNode(String name, NodeState state) {
        assert classInvariants();
        write();

        MutableNodeState childState = getWriteState(name);
        if (childState == null) {
            writeState.nodes.remove(name);
            childState = createChildBuilder(name).write();
        }
        childState.reset(state);

        updated();
        return this;
    }

    @Override @Nonnull
    public NodeBuilder removeNode(String name) {
        assert classInvariants();
        write();

        if (writeState.base.getChildNode(name) != null) {
            writeState.nodes.put(name, null);
        } else {
            writeState.nodes.remove(name);
        }

        updated();
        return this;
    }

    @Override
    public long getPropertyCount() {
        assert classInvariants();
        return read().getPropertyCount();
    }

    @Override
    public Iterable<? extends PropertyState> getProperties() {
        assert classInvariants();
        return read().getProperties();
    }


    @Override
    public PropertyState getProperty(String name) {
        assert classInvariants();
        return read().getProperty(name);
    }

    @Override @Nonnull
    public NodeBuilder removeProperty(String name) {
        assert classInvariants();
        write();

        if (writeState.base.getProperty(name) != null) {
            writeState.properties.put(name, null);
        } else {
            writeState.properties.remove(name);
        }

        updated();
        return this;
    }

    @Override
    public NodeBuilder setProperty(PropertyState property) {
        assert classInvariants();
        write();
        writeState.properties.put(property.getName(), property);
        updated();
        return this;
    }

    @Override
    public <T> NodeBuilder setProperty(String name, T value) {
        setProperty(PropertyStates.createProperty(name, value));
        return this;
    }

    @Override
    public <T> NodeBuilder setProperty(String name, T value, Type<T> type) {
        setProperty(PropertyStates.createProperty(name, value, type));
        return this;
    }

    @Override
    public NodeBuilder child(String name) {
        assert classInvariants();
        read(); // shortcut when dealing with a read-only child node
        if (hasBaseState(name)
                && (writeState == null
                    || (writeState.base == baseState && !writeState.nodes.containsKey(name)))) {
            return createChildBuilder(name);
        }

        // no read-only child node found, switch to write mode
        write();

        if (getWriteState(name) == null) {
            NodeState childBase;
            if (removed(name)) {
                // The child node was removed earlier and we're creating
                // a new child with the same name. Use the null state to
                // prevent the previous child state from re-surfacing.
                childBase = null;
            }
            else {
                childBase = getBaseState(name);
            }

            writeState.nodes.put(name, new MutableNodeState(childBase));
        }

        MemoryNodeBuilder builder = createChildBuilder(name);
        builder.write();
        return builder;
    }

    /**
     * The <em>mutable</em> state being built. Instances of this class
     * are never passed beyond the containing {@code MemoryNodeBuilder},
     * so it's not a problem that we intentionally break the immutability
     * assumption of the {@link NodeState} interface.
     */
    private class MutableNodeState extends AbstractNodeState {

        /**
         * The immutable base state.
         */
        private NodeState base;

        /**
         * Set of added, modified or removed ({@code null} value)
         * property states.
         */
        private final Map<String, PropertyState> properties =
                Maps.newHashMap();

        /**
         * Set of added, modified or removed ({@code null} value)
         * child nodes.
         */
        private final Map<String, MutableNodeState> nodes =
                Maps.newHashMap();

        /**
         * Determine whether the a node with the given name was removed
         * @param name  name of the node
         * @return  {@code true}  iff a node with the given name was removed
         */
        private boolean isRemoved(String name) {
            return nodes.containsKey(name) && nodes.get(name) == null;
        }

        public MutableNodeState(NodeState base) {
            if (base != null) {
                this.base = base;
            } else {
                this.base = MemoryNodeState.EMPTY_NODE;
            }
        }

        public NodeState snapshot() {
            Map<String, NodeState> nodes = Maps.newHashMap();
            for (Map.Entry<String, MutableNodeState> entry : this.nodes.entrySet()) {
                String name = entry.getKey();
                MutableNodeState node = entry.getValue();
                NodeState before = base.getChildNode(name);
                if (node == null) {
                    if (before != null) {
                        nodes.put(name, null);
                    }
                } else {
                    NodeState after = node.snapshot();
                    if (after != before) {
                        nodes.put(name, after);
                    }
                }
            }
            return with(base, Maps.newHashMap(this.properties), nodes);
        }

        void reset(NodeState newBase) {
            base = newBase;
            properties.clear();

            Iterator<Map.Entry<String, MutableNodeState>> iterator =
                    nodes.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, MutableNodeState> entry = iterator.next();
                MutableNodeState cstate = entry.getValue();
                NodeState cbase = newBase.getChildNode(entry.getKey());
                if (cbase == null || cstate == null) {
                    iterator.remove();
                } else {
                    cstate.reset(cbase);
                }
            }
        }

        //-----------------------------------------------------< NodeState >--

        @Override
        public long getPropertyCount() {
            return withProperties(base, properties).getPropertyCount();
        }

        @Override
        public PropertyState getProperty(String name) {
            return withProperties(base, properties).getProperty(name);
        }

        @Override @Nonnull
        public Iterable<? extends PropertyState> getProperties() {
            Map<String, PropertyState> copy = Maps.newHashMap(properties);
            return withProperties(base, copy).getProperties();
        }

        @Override
        public long getChildNodeCount() {
            return withNodes(base, nodes).getChildNodeCount();
        }

        @Override
        public boolean hasChildNode(String name) {
            return withNodes(base, nodes).hasChildNode(name);
        }

        @Override
        public NodeState getChildNode(String name) {
            return withNodes(base, nodes).getChildNode(name); // mutable
        }

        @Override @Nonnull
        public Iterable<String> getChildNodeNames() {
            Map<String, MutableNodeState> copy = Maps.newHashMap(nodes);
            return withNodes(base, copy).getChildNodeNames();
        }

        @Override
        public void compareAgainstBaseState(NodeState base, NodeStateDiff diff) {
            with(this.base, properties, nodes).compareAgainstBaseState(base, diff);
        }

        @Override @Nonnull
        public NodeBuilder builder() {
            throw new UnsupportedOperationException();
        }

    }

}
