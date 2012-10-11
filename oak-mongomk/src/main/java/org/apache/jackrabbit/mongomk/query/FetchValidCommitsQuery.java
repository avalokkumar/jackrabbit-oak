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
package org.apache.jackrabbit.mongomk.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.mongomk.impl.MongoConnection;
import org.apache.jackrabbit.mongomk.model.CommitMongo;
import org.apache.jackrabbit.mongomk.model.NodeMongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

/**
 * FIXME - Clean up the constructors.
 *
 * An query for fetching valid commits.
 */
public class FetchValidCommitsQuery extends AbstractQuery<List<CommitMongo>> {

    private static final int LIMITLESS = 0;
    private static final Logger LOG = LoggerFactory.getLogger(FetchValidCommitsQuery.class);

    private long fromRevisionId = 0L;
    private long toRevisionId = Long.MAX_VALUE;
    private int maxEntries = LIMITLESS;
    private boolean includeBranchCommits = true;

    /**
     * Constructs a new {@link FetchValidCommitsQuery} with 0 fromRevisionId
     * and limitless maxEntries.
     *
     * @param mongoConnection Mongo connection.
     * @param toRevisionId To revision id.
     */
    public FetchValidCommitsQuery(MongoConnection mongoConnection, long toRevisionId) {
        super(mongoConnection);
        this.toRevisionId = toRevisionId;
    }

    /**
     * Constructs a new {@link FetchValidCommitsQuery}
     *
     * @param mongoConnection Mongo connection.
     * @param fromRevisionId From revision id.
     * @param toRevisionId To revision id.
     * @param maxEntries Max number of entries that should be fetched.
     */
    public FetchValidCommitsQuery(MongoConnection mongoConnection, long fromRevisionId,
            long toRevisionId, int maxEntries) {
        super(mongoConnection);
        this.fromRevisionId = fromRevisionId;
        this.toRevisionId = toRevisionId;
        this.maxEntries = maxEntries;
    }

    /**
     * Sets whether the branch commits are included in the query.
     *
     * @param includeBranchCommits Whether the branch commits are included.
     */
    public void includeBranchCommits(boolean includeBranchCommits) {
        this.includeBranchCommits = includeBranchCommits;
    }

    @Override
    public List<CommitMongo> execute() {
        DBCursor dbCursor = fetchListOfValidCommits();
        List<CommitMongo> commits = convertToCommits(dbCursor);
        return commits;
    }

    private List<CommitMongo> convertToCommits(DBCursor dbCursor) {
        Map<Long, CommitMongo> revisions = new HashMap<Long, CommitMongo>();
        while (dbCursor.hasNext()) {
            CommitMongo commitMongo = (CommitMongo) dbCursor.next();
            revisions.put(commitMongo.getRevisionId(), commitMongo);
        }

        List<CommitMongo> validCommits = new LinkedList<CommitMongo>();
        if (revisions.isEmpty()) {
            return validCommits;
        }

        Long currentRevision = toRevisionId;
        if (!revisions.containsKey(currentRevision)) {
            currentRevision = Collections.max(revisions.keySet());
        }

        while (true) {
            CommitMongo commitMongo = revisions.get(currentRevision);
            if (commitMongo == null) {
                break;
            }
            validCommits.add(commitMongo);
            Long baseRevision = commitMongo.getBaseRevId();
            if ((currentRevision == 0L) || (baseRevision == null || baseRevision < fromRevisionId)) {
                break;
            }
            currentRevision = baseRevision;
        }

        LOG.debug(String.format("Found list of valid revisions for max revision %s: %s", toRevisionId, validCommits));

        return validCommits;
    }

    private DBCursor fetchListOfValidCommits() {
        DBCollection commitCollection = mongoConnection.getCommitCollection();
        QueryBuilder queryBuilder = QueryBuilder.start(CommitMongo.KEY_FAILED).notEquals(Boolean.TRUE)
                .and(CommitMongo.KEY_REVISION_ID).lessThanEquals(toRevisionId);

        if (!includeBranchCommits) {
            queryBuilder = queryBuilder.and(new BasicDBObject(NodeMongo.KEY_BRANCH_ID,
                    new BasicDBObject("$exists", false)));
        }

        DBObject query = queryBuilder.get();

        LOG.debug(String.format("Executing query: %s", query));

        return commitCollection.find(query).limit(maxEntries);
    }
}
