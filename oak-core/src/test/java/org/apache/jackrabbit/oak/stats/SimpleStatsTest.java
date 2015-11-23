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

package org.apache.jackrabbit.oak.stats;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimpleStatsTest {

    @Test
    public void usageTest() throws Exception {
        AtomicLong counter = new AtomicLong();
        SimpleStats stats = new SimpleStats(counter);

        stats.mark();
        assertEquals(1, counter.get());
        assertEquals(1, stats.getCount());

        stats.inc();
        assertEquals(2, counter.get());

        stats.dec();
        assertEquals(1, counter.get());

        stats.mark(2);
        assertEquals(3, counter.get());

        counter.set(0);
        stats.update(100, TimeUnit.SECONDS);
        assertEquals(TimeUnit.MILLISECONDS.convert(100, TimeUnit.SECONDS), counter.get());
    }

    @Test
    public void noopTest() throws Exception {
        NoopStats noop = NoopStats.INSTANCE;
        assertEquals(0, noop.getCount());

        noop.mark();
        assertEquals(0, noop.getCount());

        noop.mark(10);
        assertEquals(0, noop.getCount());

        noop.dec();
        assertEquals(0, noop.getCount());

        noop.inc();
        assertEquals(0, noop.getCount());

        noop.update(100, TimeUnit.SECONDS);
        assertEquals(0, noop.getCount());
    }
}
