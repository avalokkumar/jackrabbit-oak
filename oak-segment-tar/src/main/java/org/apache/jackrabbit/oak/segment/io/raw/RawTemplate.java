/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jackrabbit.oak.segment.io.raw;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Arrays;
import java.util.Objects;

/**
 * A node template record. This record contains information of a node that don't
 * change frequently.
 */
public class RawTemplate {

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private RawRecordId primaryType;

        private RawRecordId[] mixins;

        private boolean manyChildNodes;

        private boolean noChildNodes;

        private RawRecordId childNodeName;

        private RawRecordId propertyNames;

        private byte[] propertyTypes;

        private Builder() {
            // Prevent external instantiation.
        }

        Builder withPrimaryType(RawRecordId primaryType) {
            this.primaryType = checkNotNull(primaryType);
            return this;
        }

        Builder withMixins(RawRecordId[] mixins) {
            this.mixins = checkNotNull(mixins);
            return this;
        }

        Builder withManyChildNodes() {
            this.manyChildNodes = true;
            this.noChildNodes = false;
            return this;
        }

        Builder withNoChildNodes() {
            this.noChildNodes = true;
            this.manyChildNodes = false;
            return this;
        }

        Builder withChildNodeName(RawRecordId childNodeName) {
            this.childNodeName = checkNotNull(childNodeName);
            return this;
        }

        Builder withPropertyNames(RawRecordId propertyNames) {
            this.propertyNames = checkNotNull(propertyNames);
            return this;
        }

        Builder withPropertyTypes(byte[] propertyTypes) {
            this.propertyTypes = checkNotNull(propertyTypes);
            return this;
        }

        RawTemplate build() {
            if (childNodeName != null) {
                checkState(!noChildNodes, "no child nodes and child name provided");
                checkState(!manyChildNodes, "many child nodes and child name provided");
            }
            return new RawTemplate(this);
        }

    }

    private final RawRecordId primaryType;

    private final RawRecordId[] mixins;

    private final boolean manyChildNodes;

    private final boolean noChildNodes;

    private final RawRecordId childNodeName;

    private final RawRecordId propertyNames;

    private final byte[] propertyTypes;

    private RawTemplate(Builder builder) {
        this.primaryType = builder.primaryType;
        this.mixins = builder.mixins;
        this.manyChildNodes = builder.manyChildNodes;
        this.noChildNodes = builder.noChildNodes;
        this.childNodeName = builder.childNodeName;
        this.propertyNames = builder.propertyNames;
        this.propertyTypes = builder.propertyTypes;
    }

    /**
     * Return a pointer to a string record that stores the node's primary type.
     *
     * @return An instance of {@link RawRecordId}. It can be {@code null}.
     */
    public RawRecordId getPrimaryType() {
        return primaryType;
    }

    /**
     * Return an array of pointers to string records, each of them storing the
     * node's mixin types.
     *
     * @return An array of {@link RawRecordId}. It can be {@code null}.
     */
    public RawRecordId[] getMixins() {
        return mixins;
    }

    /**
     * Return {@code true} if this node has more than one child node.
     *
     * @return a boolean.
     */
    public boolean hasManyChildNodes() {
        return manyChildNodes;
    }

    /**
     * Return {@code true} if this node has no child nodes.
     *
     * @return a boolean.
     */
    public boolean hasNoChildNodes() {
        return noChildNodes;
    }

    /**
     * Return a pointer to a string representing the name of the only child of
     * the node. This method returns a non-{@code null} value iff both {@link
     * #hasManyChildNodes()} and {@link #hasNoChildNodes()} return {@code
     * false}.
     *
     * @return an instance of {@link RawRecordId}. It can be {@code null}.
     */
    public RawRecordId getChildNodeName() {
        return childNodeName;
    }

    /**
     * Return a pointer to a list record containing the names of the properties
     * of the node. The number of elements in the list record is given by the
     * length of the array returned by {@link #getPropertyTypes()}. If the node
     * has no properties, this method returns {@code null}.
     *
     * @return an instance of {@link RawRecordId}. It can be {@code null}.
     */
    public RawRecordId getPropertyNames() {
        return propertyNames;
    }

    /**
     * Return an array of property types, one for each proeprty of the node.
     * This method returns {@code null} if the node has no properties.
     *
     * @return an array of bytes. It can be {@code null}.
     */
    public byte[] getPropertyTypes() {
        return propertyTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        return equals((RawTemplate) o);
    }

    private boolean equals(RawTemplate that) {
        return manyChildNodes == that.manyChildNodes &&
                noChildNodes == that.noChildNodes &&
                Objects.equals(primaryType, that.primaryType) &&
                Arrays.equals(mixins, that.mixins) &&
                Objects.equals(childNodeName, that.childNodeName) &&
                Objects.equals(propertyNames, that.propertyNames) &&
                Arrays.equals(propertyTypes, that.propertyTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryType, mixins, manyChildNodes, noChildNodes, childNodeName, propertyNames, propertyTypes);
    }

    @Override
    public String toString() {
        return String.format(
                "RawTemplate{primaryType=%s, mixins=%s, manyChildNodes=%s, zeroChildNodes=%s, childNodeName=%s, propertyNames=%s, propertyTypes=%s}",
                primaryType,
                Arrays.toString(mixins),
                manyChildNodes,
                noChildNodes,
                childNodeName,
                propertyNames,
                Arrays.toString(propertyTypes)
        );
    }
}
