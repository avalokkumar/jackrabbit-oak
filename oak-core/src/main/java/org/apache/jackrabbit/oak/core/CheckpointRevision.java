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

package org.apache.jackrabbit.oak.core;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.api.Revision;

/**
 * A revision implementation based on a {@link org.apache.jackrabbit.oak.spi.state.NodeStore}
 * checkpoint.
 */
final class CheckpointRevision implements Revision {

    final String checkpoint;

    public CheckpointRevision(@Nonnull String checkpoint) {
        this.checkpoint = checkNotNull(checkpoint);
    }

    @Override
    public String asString() {
        return checkpoint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        CheckpointRevision that = (CheckpointRevision) other;
        return checkpoint.equals(that.checkpoint);
    }

    @Override
    public int hashCode() {
        return checkpoint.hashCode();
    }

    @Override
    public String toString() {
        return "CheckpointRevision{checkpoint='" + checkpoint + '\'' + '}';
    }
}
