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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;
import org.apache.jackrabbit.oak.cache.AbstractCacheStats;
import org.apache.jackrabbit.oak.segment.CacheWeights.SegmentCacheWeigher;

/**
 * A cache for {@link SegmentId#isDataSegmentId() data} {@link Segment} instances by their
 * {@link SegmentId}. This cache ignores {@link SegmentId#isBulkSegmentId() bulk} segments.
 * <p>
 * Conceptually this cache serves as a 2nd level cache for segments. The 1st level cache is
 * implemented by memoising the segment in its id (see {@link SegmentId#segment}. Every time
 * an segment is evicted from this cache the memoised segment is discarded (see
 * {@link SegmentId#unloaded()}).
 */
public class SegmentCache {
    /** Default maximum weight of this cache in MB */
    public static final int DEFAULT_SEGMENT_CACHE_MB = 256;

    /** Weigher to determine the current weight of all items in this cache */
    private final Weigher<SegmentId, Segment> weigher = new SegmentCacheWeigher();

    /** Maximum weight of the items in this cache */
    private final long maximumWeight;

    /** Cache of recently accessed segments */
    @Nonnull
    private final Cache<SegmentId, Segment> cache;

    @Nonnull
    private final Stats stats = new Stats("Segment Cache");

    /**
     * Create a new segment cache of the given size.
     * @param cacheSizeMB  size of the cache in megabytes.
     */
    public SegmentCache(long cacheSizeMB) {
        this.maximumWeight = cacheSizeMB * 1024 * 1024;
        this.cache = CacheBuilder.newBuilder()
                .concurrencyLevel(16)
                .maximumWeight(maximumWeight)
                .weigher(weigher)
                .removalListener(this::onRemove)
                .build();
    }

    /**
     * Create a new segment cache with the {@link #DEFAULT_SEGMENT_CACHE_MB default size}.
     */
    public SegmentCache() {
        this(DEFAULT_SEGMENT_CACHE_MB);
    }

    /**
     * Removal handler called whenever an item is evicted from the cache. Propagates
     * to {@link SegmentId#unloaded()}.
     */
    private void onRemove(@Nonnull RemovalNotification<SegmentId, Segment> notification) {
        SegmentId id = notification.getKey();
        if (id != null) {
            Segment segment = notification.getValue();
            if (segment != null) {
                stats.currentWeight.addAndGet(-weigher.weigh(id, segment));
            }
            stats.evictionCount.incrementAndGet();
            id.unloaded();
        }
    }

    /** Unconditionally put an item in the cache */
    private Segment put(@Nonnull SegmentId id, @Nonnull Segment segment) {
        // Call loaded *before* putting the segment into the cache as the latter
        // might cause it to get evicted right away again.
        id.loaded(segment);
        cache.put(id, segment);
        stats.currentWeight.addAndGet(weigher.weigh(id, segment));
        return segment;
    }

    /**
     * Retrieve an segment from the cache or load it and cache it if not yet in the cache.
     * @param id        the id of the segment
     * @param loader    the loader to load the segment if not yet in the cache
     * @return          the segment identified by {@code id}
     * @throws ExecutionException  when {@code loader} failed to load an segment
     */
    @Nonnull
    public Segment getSegment(@Nonnull final SegmentId id, @Nonnull final Callable<Segment> loader)
    throws ExecutionException {
        Segment segment = id.getCachedSegment();
        if (segment == null) {
            synchronized (id) {
                segment = id.getCachedSegment();
                if (segment != null) {
                    stats.hitCount.incrementAndGet();
                    return segment;
                }
            }
        } else {
            stats.hitCount.incrementAndGet();
            return segment;
        }

        // Load bulk segment directly without putting it in cache
        try {
            if (id.isBulkSegmentId()) {
                return loader.call();
            }
        } catch (Exception e) {
            throw new ExecutionException(e);
        }

        // Load data segment and put it in the cache
        try {
            long t0 = System.nanoTime();
            segment = loader.call();
            stats.loadSuccessCount.incrementAndGet();
            stats.loadTime.addAndGet(System.nanoTime() - t0);
            stats.missCount.incrementAndGet();

            return put(id, segment);
        } catch (Exception e) {
            stats.loadExceptionCount.incrementAndGet();
            throw new ExecutionException(e);
        }
    }

    /**
     * Put a segment into the cache. This method does nothing for
     * {@link SegmentId#isBulkSegmentId() bulk} segments.
     * @param segment  the segment to cache
     */
    public void putSegment(@Nonnull Segment segment) {
        SegmentId id = segment.getSegmentId();
        if (!id.isBulkSegmentId()) {
            put(id, segment);
        }
    }

    /**
     * Clear all segment from the cache
     */
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * @return  Statistics for this cache.
     */
    @Nonnull
    public AbstractCacheStats getCacheStats() {
        return stats;
    }

    /** We cannot rely on the statistics of the underlying Guava cache as all cache hits
     * are taken by {@link SegmentId#getSegment()} and thus never seen by the cache.
     */
    private class Stats extends AbstractCacheStats {
        @Nonnull
        final AtomicLong currentWeight = new AtomicLong();

        @Nonnull
        final AtomicLong loadSuccessCount = new AtomicLong();

        @Nonnull
        final AtomicInteger loadExceptionCount = new AtomicInteger();

        @Nonnull
        final AtomicLong loadTime = new AtomicLong();

        @Nonnull
        final AtomicLong evictionCount = new AtomicLong();

        @Nonnull
        final AtomicLong hitCount = new AtomicLong();

        @Nonnull
        final AtomicLong missCount = new AtomicLong();

        protected Stats(@Nonnull String name) {
            super(name);
        }

        @Override
        protected com.google.common.cache.CacheStats getCurrentStats() {
            return new com.google.common.cache.CacheStats(
                    hitCount.get(),
                    missCount.get(),
                    loadSuccessCount.get(),
                    loadExceptionCount.get(),
                    loadTime.get(),
                    evictionCount.get());
        }

        @Override
        public long getElementCount() {
            return cache.size();
        }

        @Override
        public long getMaxTotalWeight() {
            return maximumWeight;
        }

        @Override
        public long estimateCurrentWeight() {
            return currentWeight.get();
        }
    }

}