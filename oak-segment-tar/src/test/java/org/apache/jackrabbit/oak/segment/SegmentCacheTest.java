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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.cache.AbstractCacheStats;
import org.junit.Test;

public class SegmentCacheTest {
    private SegmentId id1;
    private Segment segment1;
    private SegmentId id2;
    private Segment segment2;
    private final SegmentCache cache = new SegmentCache();

    private final SegmentStore store = new SegmentStore() {
        {
            id1 = new SegmentId(this, -1, -1);
            segment1 = mock(Segment.class);
            when(segment1.getSegmentId()).thenReturn(id1);
            id2 = new SegmentId(this, -1, -2);
            segment2 = mock(Segment.class);
            when(segment2.getSegmentId()).thenReturn(id2);
        }

        @Override
        public boolean containsSegment(SegmentId id) {
            return id.equals(id1) || id.equals(id2);
        }

        @Nonnull
        @Override
        public Segment readSegment(SegmentId id) {
            if (id1.equals(id)) {
                return segment1;
            } else if (id2.equals(id)) {
                return segment2;
            } else {
                throw new SegmentNotFoundException(id);
            }
        }

        @Override
        public void writeSegment(SegmentId id, byte[] bytes, int offset, int length) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    public void putTest() throws ExecutionException {
        cache.putSegment(segment1);
        assertEquals(segment1, cache.getSegment(id1, () -> {
            throw new IllegalStateException("Segment should be cached");
        }));
    }

    @Test
    public void invalidateTests() throws ExecutionException {
        cache.putSegment(segment1);
        assertEquals(segment1, cache.getSegment(id1, () -> {
            throw new IllegalStateException("Segment should be cached");
        }));

        // Clearing the cache should cause an eviction call back for id
        cache.clear();

        // Check that segment1 was evicted and needs reloading
        AtomicBoolean cached = new AtomicBoolean(true);
        assertEquals(segment1, cache.getSegment(id1, () -> {
            cached.set(false);
            return segment1;
        }));
        assertFalse(cached.get());
    }

    @Test
    public void statsTest() throws Exception {
        AbstractCacheStats stats = cache.getCacheStats();

        // empty cache
        assertEquals(0, stats.getElementCount());
        assertEquals(0, stats.getLoadCount());
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0, stats.getRequestCount());

        // load
        cache.getSegment(id1, () -> segment1);
        assertEquals(1, stats.getElementCount());
        assertEquals(1, stats.getLoadCount());
        assertEquals(0, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(1, stats.getRequestCount());

        // cache hit
        cache.getSegment(id1, () -> {
            throw new IllegalStateException("Segment should be cached");
        });
        assertEquals(1, stats.getElementCount());
        assertEquals(1, stats.getLoadCount());
        assertEquals(1, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(2, stats.getRequestCount());

        cache.clear();
        assertEquals(0, stats.getElementCount());
        assertEquals(1, stats.getLoadCount());
        assertEquals(1, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(2, stats.getRequestCount());

        stats.resetStats();
        assertEquals(0, stats.getElementCount());
        assertEquals(0, stats.getLoadCount());
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0, stats.getRequestCount());
    }

}