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

package org.apache.jackrabbit.oak.segment;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.Thread.currentThread;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.google.common.base.Supplier;
import com.google.common.util.concurrent.Monitor;
import com.google.common.util.concurrent.Monitor.Guard;

/**
 * This {@link WriteOperationHandler} uses a pool of {@link SegmentBufferWriter}s,
 * which it passes to its {@link #execute(WriteOperation) execute} method.
 * <p>
 * Instances of this class are thread safe. See also the class comment of
 * {@link SegmentWriter}.
 */
public class SegmentBufferWriterPool implements WriteOperationHandler {
    private final Map<Object, SegmentBufferWriter> writers = newHashMap();
    private final Set<SegmentBufferWriter> borrowed = newHashSet();
    private final Set<SegmentBufferWriter> disposed = newHashSet();

    @Nonnull
    private final SegmentStore store;

    @Nonnull
    private final SegmentTracker tracker;

    @Nonnull
    private final SegmentReader reader;

    @Nonnull
    private final Supplier<Integer> gcGeneration;

    @Nonnull
    private final SegmentVersion version;

    @Nonnull
    private final String wid;

    private short writerId = -1;

    public SegmentBufferWriterPool(
            @Nonnull SegmentStore store,
            @Nonnull SegmentTracker tracker,
            @Nonnull SegmentReader reader,
            @Nonnull SegmentVersion version,
            @Nonnull String wid,
            @Nonnull Supplier<Integer> gcGeneration) {
        this.store = checkNotNull(store);
        this.tracker = checkNotNull(tracker);
        this.reader = checkNotNull(reader);
        this.version = checkNotNull(version);
        this.wid = checkNotNull(wid);
        this.gcGeneration = checkNotNull(gcGeneration);
    }

    @Override
    public RecordId execute(WriteOperation writeOperation) throws IOException {
        SegmentBufferWriter writer = borrowWriter(currentThread());
        try {
            return writeOperation.execute(writer);
        } finally {
            returnWriter(currentThread(), writer);
        }
    }

    private final Monitor poolMonitor = new Monitor(true);
    private final Monitor flushMonitor = new Monitor(true);

    @Override
    public void flush() throws IOException {
        flushMonitor.enter();
        try {
            List<SegmentBufferWriter> toFlush = newArrayList();
            List<SegmentBufferWriter> toReturn = newArrayList();
            poolMonitor.enter();
            try {
                toFlush.addAll(writers.values());
                writers.clear();
                toReturn.addAll(borrowed);
                borrowed.clear();
            } finally {
                poolMonitor.leave();
            }

            if (safeEnterWhen(poolMonitor, allReturned(toReturn))) {
                try {
                    toFlush.addAll(disposed);
                    disposed.clear();
                } finally {
                    poolMonitor.leave();
                }

                // Call flush from outside a synchronized context to avoid
                // deadlocks of that method calling SegmentStore.writeSegment
                for (SegmentBufferWriter writer : toFlush) {
                    writer.flush();
                }
            }
        } finally {
            flushMonitor.leave();
        }
    }

    @Nonnull
    private Guard allReturned(final List<SegmentBufferWriter> toReturn) {
        poolMonitor.enter();
        try {
            return new Guard(poolMonitor) {
                @Override
                public boolean isSatisfied() {
                    return disposed.containsAll(toReturn);
                }
            };
        } finally {
            poolMonitor.leave();
        }
    }

    private static boolean safeEnterWhen(Monitor monitor, Guard guard) {
        try {
            monitor.enterWhen(guard);
            return true;
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private SegmentBufferWriter borrowWriter(Object key) {
        poolMonitor.enter();
        try {
            SegmentBufferWriter writer = writers.remove(key);
            if (writer == null) {
                writer = new SegmentBufferWriter(store, tracker, reader, version, getWriterId(wid), gcGeneration.get());
            } else if (writer.getGeneration() != gcGeneration.get()) {
                disposed.add(writer);
                writer = new SegmentBufferWriter(store, tracker, reader, version, getWriterId(wid), gcGeneration.get());
            }
            borrowed.add(writer);
            return writer;
        } finally {
            poolMonitor.leave();
        }
    }

    private void returnWriter(Object key, SegmentBufferWriter writer) {
        poolMonitor.enter();
        try {
            if (borrowed.remove(writer)) {
                checkState(writers.put(key, writer) == null);
            } else {
                // Defer flush this writer as it was borrowed while flush() was called.
                disposed.add(writer);
            }
        } finally {
            poolMonitor.leave();
        }
    }

    private String getWriterId(String wid) {
        if (++writerId > 9999) {
            writerId = 0;
        }
        // Manual padding seems to be fastest here
        if (writerId < 10) {
            return wid + ".000" + writerId;
        } else if (writerId < 100) {
            return wid + ".00" + writerId;
        } else if (writerId < 1000) {
            return wid + ".0" + writerId;
        } else {
            return wid + "." + writerId;
        }
    }
}
