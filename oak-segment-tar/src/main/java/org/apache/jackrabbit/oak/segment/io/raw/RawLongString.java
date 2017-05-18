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

import java.util.Objects;

/**
 * A long string record. A string is serialized as a long string when it can't
 * be comfortably stored in a segment. In this case, a pointer to the value of
 * the string is returned.
 */
public class RawLongString extends RawString {

    private final RawRecordId recordId;

    private final int length;

    RawLongString(RawRecordId recordId, int length) {
        this.recordId = recordId;
        this.length = length;
    }

    /**
     * Return the pointer to the string data.
     *
     * @return An instance of {@link RawRecordId}.
     */
    public RawRecordId getRecordId() {
        return recordId;
    }

    /**
     * Return the length of the string.
     *
     * @return A positive integer.
     */
    public int getLength() {
        return length;
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
        return equals((RawLongString) o);
    }

    private boolean equals(RawLongString that) {
        return length == that.length && Objects.equals(recordId, that.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, length);
    }

    @Override
    public String toString() {
        return String.format("RawLongString{recordId=%s, length=%d}", recordId, length);
    }

}