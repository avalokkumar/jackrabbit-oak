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

import static com.google.common.collect.Queues.newConcurrentLinkedQueue;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Segment identifier. There are two types of segments: data segments, and bulk
 * segments. Data segments have a header and may reference other segments; bulk
 * segments do not.
 */
public class SegmentId implements Comparable<SegmentId> {

    /** Logger instance */
    private static final Logger log = LoggerFactory.getLogger(SegmentId.class);

    /**
     * Checks whether this is a data segment identifier.
     *
     * @return {@code true} for a data segment, {@code false} otherwise
     */
    public static boolean isDataSegmentId(long lsb) {
        return (lsb >>> 60) == 0xAL;
    }

    @Nonnull
    private final SegmentStore store;

    @Nonnull
    private final SegmentCache segmentCache;

    private final long msb;

    private final long lsb;

    private final long creationTime;

    /**
     * The gc generation of this segment or -1 if unknown.
     */
    private volatile int gcGeneration = -1;

    /**
     * The gc info of this segment if it has been reclaimed or {@code null} otherwise.
     */
    @CheckForNull
    private String gcInfo;

    /**
     * A reference to the segment object, if it is available in memory. It is
     * used for fast lookup.
     */
    private volatile Segment segment;

    public SegmentId(@Nonnull SegmentStore store, @Nonnull SegmentCache segmentCache, long msb, long lsb) {
        this.store = store;
        this.segmentCache = segmentCache;
        this.msb = msb;
        this.lsb = lsb;
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Checks whether this is a data segment identifier.
     *
     * @return {@code true} for a data segment, {@code false} otherwise
     */
    public boolean isDataSegmentId() {
        return isDataSegmentId(lsb);
    }

    /**
     * Checks whether this is a bulk segment identifier.
     *
     * @return {@code true} for a bulk segment, {@code false} otherwise
     */
    public boolean isBulkSegmentId() {
        return (lsb >>> 60) == 0xBL;
    }

    public long getMostSignificantBits() {
        return msb;
    }

    public long getLeastSignificantBits() {
        return lsb;
    }

    // michid abstract
    public static class SegmentCache {
        // michid don't cache binaries
        private final int cacheSize;
        private final ConcurrentLinkedQueue<Segment> segments = newConcurrentLinkedQueue();
        private final AtomicLong currentSize = new AtomicLong();

        public SegmentCache(int cacheSize) {this.cacheSize = cacheSize;}

        public void cache(SegmentId id, Segment segment) {
            long size = segment.getCacheSize();
            id.segment = segment;
            segments.add(segment);
            currentSize.addAndGet(size);
            log.debug("Added segment {} to tracker cache ({} bytes)", id, size);

            int failedEvictions = 0;
            while (currentSize.get() > cacheSize) {
                Segment head = segments.poll();
                if (head == null) {
                    return;
                }
                SegmentId headId = head.getSegmentId();
                if (head.accessed()) {
                    failedEvictions++;
                    segments.add(head);
                    log.debug("Segment {} was recently used, keeping in cache", headId);
                } else {
                    headId.segment = null;
                    long lastSize = head.getCacheSize();
                    currentSize.addAndGet(-lastSize);
                    log.debug("Removed segment {} from tracker cache ({} bytes) after " +
                            "{} eviction attempts", headId, lastSize);
                }
            }
        }
    }

    /**
     * Get the segment identified by this instance. The segment is memoised in this instance's
     * {@link #segment} field.
     * @return  the segment identified by this instance.
     * @see #loaded(Segment)
     */
    @Nonnull
    public Segment getSegment() {
        Segment segment = this.segment;
        if (segment == null) {
            synchronized (this) {
                segment = this.segment;
                if (segment == null) {
                    try {
                        log.debug("Loading segment {}", this);
                        segment = store.readSegment(this);
                        loaded(segment, true);
                    } catch (SegmentNotFoundException snfe) {
                        log.error("Segment not found: {}. {}", this, gcInfo(), snfe);
                        throw snfe;
                    }
                }
            }
        }
        segment.access();
        return segment;
    }

    @Nonnull
    private String gcInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("SegmentId age=").append(System.currentTimeMillis() - creationTime).append("ms");
        if (gcInfo != null) {
            sb.append(",").append(gcInfo);
        }
        if (gcGeneration >= 0) {
            sb.append(",").append("segment-generation=").append(gcGeneration);
        }
        return sb.toString();
    }

    /* For testing only */
    @CheckForNull
    String getGcInfo() {
        return gcInfo;
    }

    /**
     * Notify this id about the reclamation of its segment (e.g. by
     * the garbage collector).
     * @param gcInfo  details about the reclamation. This information
     *                is logged along with the {@code SegmentNotFoundException}
     *                when attempting to resolve the segment of this id.
     */
    public void reclaimed(@Nonnull String gcInfo) {
        this.gcInfo = gcInfo;
    }

    /**
     * This method should only be called from lower level caches to notify this instance that the
     * passed {@code segment} has been loaded and should be memoised.
     * @param segment  segment with this id. If the id doesn't match the behaviour is undefined.
     * @see #getSegment()
     * michid visibility, hack
     */
    public void loaded(@Nonnull Segment segment, boolean cache) {
        if (cache) {
            segmentCache.cache(this, segment);
        } else {
            this.segment = segment;
        }
        gcGeneration = segment.getGcGeneration();
    }

    /**
     * Determine whether this instance belongs to the passed {@code store}
     * @param store
     * @return  {@code true} iff this instance belongs to {@code store}
     */
    public boolean sameStore(@Nonnull SegmentStore store) {
        return this.store == store;
    }

    public long getCreationTime() {
        return creationTime;
    }

    /**
     * @return  this segment id as UUID
     */
    public UUID asUUID() {
        return new UUID(msb, lsb);
    }

    /**
     * Get the underlying segment's gc generation. Might cause the segment to
     * get loaded if the generation info is missing
     * @return the segment's gc generation
     */
    public int getGcGeneration() {
        if (gcGeneration < 0) {
            return getSegment().getGcGeneration();
        } else {
            return gcGeneration;
        }
    }

    // --------------------------------------------------------< Comparable >--

    @Override
    public int compareTo(SegmentId that) {
        int d = Long.valueOf(this.msb).compareTo(that.msb);
        if (d == 0) {
            d = Long.valueOf(this.lsb).compareTo(that.lsb);
        }
        return d;
    }

    // ------------------------------------------------------------< Object >--

    @Override
    public String toString() {
        return new UUID(msb, lsb).toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof SegmentId) {
            SegmentId that = (SegmentId) object;
            return msb == that.msb && lsb == that.lsb;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) lsb;
    }

}
