/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 *
 */

package io.supertokens.storage.mongodb;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1.SessionInfoWithLastUpdated;
import io.supertokens.pluginInterface.tokenInfo.PastTokenInfo;
import io.supertokens.storage.mongodb.config.Config;
import io.supertokens.storage.mongodb.utils.Utils;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class Queries {

    // to be used in testing only
    static void deleteAllCollections(Start start) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        {
            MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());
            collection.deleteMany(new Document());
        }
        {
            MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());
            collection.deleteMany(new Document());
        }
        {
            MongoCollection collection = client.getCollection(Config.getConfig(start).getPastTokensCollection());
            collection.deleteMany(new Document());
        }

    }

    private static boolean isDuplicateKeyException(Exception e) {
        return e instanceof MongoWriteException && e.getMessage().contains("duplicate key error collection");
    }

    @SuppressWarnings("unchecked")
    static boolean setKeyValue_Transaction(Start start, String key, KeyValueInfoWithLastUpdated info) {
        // here we want to do something like upsert, but not exactly that since if the user has specificed info
        // .lastUpdatedSign, then it must only be an update operation and it should not create a new document. So we
        // do an update if that is not null. Else we do an insert.

        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());

        if (info.lastUpdatedSign != null) {
            // here we only want to update an existing key value. We do not do upsert since we know that this key
            // already
            // exists. If it does not, we should not do anything and return false (since it's a part of a
            // "transaction").
            Document toUpdate = new Document("$set", new Document("value", info.value)
                    .append("created_at_time", info.createdAtTime)
                    .append("last_updated_sign", Utils.getUUID()));

            UpdateResult result = collection.updateOne(
                    Filters.and(Filters.eq("_id", key),
                            Filters.eq("last_updated_sign", info.lastUpdatedSign)),
                    toUpdate,
                    new UpdateOptions().upsert(false)
            );
            // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

            return result.getModifiedCount() == 1;

        } else {

            try {
                collection.insertOne(
                        new Document("_id", key)
                                .append("value", info.value)
                                .append("created_at_time", info.createdAtTime)
                                .append("last_updated_sign", Utils.getUUID())
                );

                // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?
                return true;
            } catch (MongoException e) {
                if (!isDuplicateKeyException(e)) {
                    throw e;
                }
            }

            return false;
        }
    }

    static void setKeyValue(Start start, String key, KeyValueInfo info) {
        // here we want to update or insert an existing key value
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());

        Document toUpdate = new Document("$set", new Document("value", info.value)
                .append("created_at_time", info.createdAtTime)
                .append("last_updated_sign", Utils.getUUID()));

        UpdateResult result = collection.updateOne(Filters.eq("_id", key),
                toUpdate,
                new UpdateOptions().upsert(true));    // the document will be created based on the _id filter above

        // TODO: supposed to call the below functions only if result.wasAcknowledged() is true. Why?

        if (result.getModifiedCount() != 1 && result.getUpsertedId() == null) {
            throw new MongoException(
                    "update / insert failed");
        }
    }

    static KeyValueInfo getKeyValue(Start start, String key) {
        KeyValueInfoWithLastUpdated result = getKeyValue_Transaction(start, key);
        if (result == null) {
            return null;
        }
        return new KeyValueInfo(result.value, result.createdAtTime);
    }

    static KeyValueInfoWithLastUpdated getKeyValue_Transaction(Start start, String key) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());
        Document result = (Document) collection.find(Filters.eq("_id", key)).first();
        if (result == null) {
            return null;
        }
        return new KeyValueInfoWithLastUpdated(result.getString("value"), result.getLong("created_at_time"),
                result.getString("last_updated_sign"));
    }

    static PastTokenInfo getPastTokenInfo(Start start, String refreshTokenHash2) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getPastTokensCollection());
        Document result = (Document) collection.find(Filters.eq("_id", refreshTokenHash2)).first();
        if (result == null) {
            return null;
        }
        return new PastTokenInfo(refreshTokenHash2, result.getString("session_handle"),
                result.getString("parent_refresh_token_hash_2"), result.getLong("created_at_time"));
    }

    @SuppressWarnings("unchecked")
    static void insertPastTokenInfo(Start start, PastTokenInfo info) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getPastTokensCollection());

        collection.insertOne(
                new Document("_id", info.refreshTokenHash2)
                        .append("parent_refresh_token_hash_2", info.parentRefreshTokenHash2)
                        .append("session_handle", info.sessionHandle)
                        .append("created_at_time", info.createdTime)
        );
    }

    static int getNumberOfPastTokens(Start start) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getPastTokensCollection());

        return Math.toIntExact(collection.countDocuments());    // this is only used in testing, so this is OK.

    }

    @SuppressWarnings("unchecked")
    static void createNewSession(Start start, String sessionHandle, String userId, String refreshTokenHash2,
                                 JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT,
                                 long createdAtTime) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        collection.insertOne(new Document("_id", sessionHandle)
                .append("user_id", userId)
                .append("refresh_token_hash_2", refreshTokenHash2)
                .append("session_data", userDataInDatabase.toString())
                .append("expires_at", expiry)
                .append("jwt_user_payload", userDataInJWT.toString())
                .append("created_at_time", createdAtTime)
                .append("last_updated_sign", Utils.getUUID()));
    }

    static boolean isSessionBlacklisted(Start start, String sessionHandle) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        return collection.find(Filters.eq("_id", sessionHandle)).first() == null;
    }

    static SessionInfoWithLastUpdated getSessionInfo_Transaction(Start start, String sessionHandle) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document result = (Document) collection.find(Filters.eq("_id", sessionHandle)).first();
        if (result == null) {
            return null;
        }

        return new SessionInfoWithLastUpdated(sessionHandle, result.getString("user_id"),
                result.getString("refresh_token_hash_2"),
                new JsonParser().parse(result.getString("session_data")).getAsJsonObject(),
                result.getLong("expires_at"),
                new JsonParser().parse(result.getString("jwt_user_payload")).getAsJsonObject(),
                result.getLong("created_at_time"),
                result.getString("last_updated_sign"));
    }

    static boolean updateSessionInfo_Transaction(Start start, String sessionHandle,
                                                 String refreshTokenHash2, long expiry, String lastUpdatedSign)
            throws StorageQueryException {

        if (lastUpdatedSign == null) {
            throw new StorageQueryException(new Exception("lastUpdatedSign cannot be null for this update operation"));
        }

        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document toUpdate = new Document("$set", new Document("refresh_token_hash_2", refreshTokenHash2)
                .append("expires_at", expiry)
                .append("last_updated_sign", Utils.getUUID()));

        UpdateResult result = collection.updateOne(
                Filters.and(Filters.eq("_id", sessionHandle),
                        Filters.eq("last_updated_sign", lastUpdatedSign)),
                toUpdate,
                new UpdateOptions().upsert(false)
        );
        // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

        return result.getModifiedCount() == 1;
    }

    static int getNumberOfSessions(Start start) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        return Math.toIntExact(collection.countDocuments());    // this is only used in testing, so this is OK.
    }

    static int deleteSession(Start start, String[] sessionHandles) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        List<Bson> filters = new ArrayList<>();
        for (String sessionHandle : sessionHandles) {
            filters.add(Filters.eq("_id", sessionHandle));
        }
        if (filters.size() > 0) {
            DeleteResult result = collection.deleteMany(Filters.or(filters));

            return Math.toIntExact(result.getDeletedCount());
        }
        return 0;
    }

    static String[] getAllSessionHandlesForUser(Start start, String userId) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());
        List<String> temp = new ArrayList<>();
        try (MongoCursor cursor = collection.find(Filters.eq("user_id", userId)).iterator()) {
            while (cursor.hasNext()) {
                Document currDoc = (Document) cursor.next();
                temp.add(currDoc.getString("_id"));
            }
        }
        String[] finalResult = new String[temp.size()];
        for (int i = 0; i < temp.size(); i++) {
            finalResult[i] = temp.get(i);
        }
        return finalResult;
    }

    static JsonObject getSessionData(Start start, String sessionHandle) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document result = (Document) collection.find(Filters.eq("_id", sessionHandle)).first();

        if (result == null) {
            return null;
        }

        return new JsonParser().parse(result.getString("session_data")).getAsJsonObject();

    }

    static int updateSessionData(Start start, String sessionHandle, JsonObject updatedData) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        UpdateResult result = collection.updateOne(
                Filters.eq("_id", sessionHandle),
                new Document("$set", new Document("session_data", updatedData.toString())
                        .append("last_updated_sign", Utils.getUUID())),
                new UpdateOptions().upsert(false)
        );
        // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

        return result.getModifiedCount() == 1 ? 1 : 0;
    }

    static void deleteAllExpiredSessions(Start start) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        collection.deleteMany(Filters.lte("expires_at", System.currentTimeMillis()));
    }

    static void deletePastOrphanedTokens(Start start, long createdBefore) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection pastTokenCollection = client.getCollection(Config.getConfig(start).getPastTokensCollection());
        MongoCollection sessionCollection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        List<Bson> toDelete = new ArrayList<>();

        // get all tokens that have created_at_time < createBefore
        try (MongoCursor cursor = pastTokenCollection.find(Filters.lt("created_at_time", createdBefore)).iterator()) {
            while (cursor.hasNext()) {

                // for each of them, we check if we need to delete them.

                Document currDoc = (Document) cursor.next();
                String refreshTokenHash2 = currDoc.getString("_id");
                String parentRefreshTokenHash2 = currDoc.getString("parent_refresh_token_hash_2");
                if (sessionCollection.find(Filters.eq("refresh_token_hash_2", refreshTokenHash2)).first() == null
                        &&
                        sessionCollection.find(Filters.eq("refresh_token_hash_2", parentRefreshTokenHash2)).first() ==
                                null) {
                    toDelete.add(Filters.eq("_id", refreshTokenHash2));
                }
            }
        }

        // we delete the necessary documents
        if (toDelete.size() > 0) {
            pastTokenCollection.deleteMany(Filters.or(toDelete));
        }
    }
}
