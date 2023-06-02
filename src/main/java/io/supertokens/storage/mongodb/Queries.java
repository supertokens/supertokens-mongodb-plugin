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
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionInfoWithLastUpdated;
import io.supertokens.storage.mongodb.config.Config;
import io.supertokens.storage.mongodb.utils.Utils;
import org.bson.Document;
import org.bson.conversions.Bson;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
            MongoCollection collection = client.getCollection(Config.getConfig(start).getJWTSigningKeysCollection());
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
                    .append("created_at_time", info.createdAtTime).append("last_updated_sign", Utils.getUUID()));

            UpdateResult result = collection.updateOne(
                    Filters.and(Filters.eq("_id", key), Filters.eq("last_updated_sign", info.lastUpdatedSign)),
                    toUpdate, new UpdateOptions().upsert(false));
            // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

            return result.getModifiedCount() == 1;

        } else {

            try {
                collection.insertOne(new Document("_id", key).append("value", info.value)
                        .append("created_at_time", info.createdAtTime).append("last_updated_sign", Utils.getUUID()));

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
                .append("created_at_time", info.createdAtTime).append("last_updated_sign", Utils.getUUID()));

        UpdateResult result = collection.updateOne(Filters.eq("_id", key), toUpdate, new UpdateOptions().upsert(true)); // the
        // document
        // will
        // be
        // created
        // based
        // on
        // the
        // _id
        // filter
        // above

        // TODO: supposed to call the below functions only if result.wasAcknowledged() is true. Why?

        if (result.getModifiedCount() != 1 && result.getUpsertedId() == null) {
            throw new MongoException("update / insert failed");
        }
    }

    static boolean deleteSessionsOfUser(Start start, String userId) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());
        DeleteResult result = collection.deleteMany(Filters.eq("user_id", userId));
        return result.getDeletedCount() > 0;
    }

    static KeyValueInfo getKeyValue(Start start, String key) throws StorageQueryException {
        KeyValueInfoWithLastUpdated result = getKeyValue_Transaction(start, key);
        if (result == null) {
            return null;
        }
        return new KeyValueInfo(result.value, result.createdAtTime);
    }

    static KeyValueInfoWithLastUpdated getKeyValue_Transaction(Start start, String key) throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());
        Document result = (Document) collection.find(Filters.eq("_id", key)).first();
        if (result == null) {
            return null;
        }
        return KeyValueInfoLastUpdatedRowMapper.getInstance().mapOrThrow(result);
    }

    static void deleteKeyValue(Start start, String key) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());

        collection.deleteOne(Filters.eq("_id", key));
    }

    static List<KeyValueInfo> getArrayKeyValue_Transaction(Start start, String key) throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());
        Document result = (Document) collection.find(Filters.eq("_id", key)).first();
        if (result == null) {
            return new ArrayList<KeyValueInfo>();
        }
        return KeyValueInfoArrayRowMapper.getInstance().mapOrThrow(result);
    }

    static boolean removeArrayKeyValuesBefore(Start start, String key, long time) throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());

        UpdateResult result = collection.updateOne(Filters.eq("_id", key),
                Updates.pullByFilter(new Document("keys", Filters.lte("created_at_time", time))));

        return result.getModifiedCount() == 1;
    }

    @SuppressWarnings("unchecked")
    static boolean addArrayKeyValue_Transaction(Start start, String key, KeyValueInfo info, Long lastCreated) {
        // here we want to do something like upsert, but not exactly that since if the user has specificed info
        // .lastUpdatedSign, then it must only be an update operation and it should not create a new document. So we
        // do an update if that is not null. Else we do an insert.

        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getKeyValueCollection());

        List<Document> keyList = Collections
                .singletonList(new Document("value", info.value).append("created_at_time", info.createdAtTime));

        if (lastCreated != null) {
            // here we only want to update an existing key value. We do not do upsert since we know that this key
            // already
            // exists. If it does not, we should not do anything and return false (since it's a part of a
            // "transaction").

            UpdateResult result = collection.updateOne(
                    Filters.and(Filters.eq("_id", key), Filters.eq("keys.0.created_at_time", lastCreated)),
                    // We have to use a pushEach with here, because it allows us to set where we push the value
                    Updates.pushEach("keys", keyList, new PushOptions().position(0)),
                    new UpdateOptions().upsert(false));
            // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

            return result.getModifiedCount() == 1;
        } else {
            try {
                UpdateResult result = collection.updateOne(
                        Filters.and(Filters.eq("_id", key), Filters.size("keys", 0)),
                        // We have to use a pushEach with here, because it allows us to set where we push the value
                        Updates.pushEach("keys", keyList, new PushOptions().position(0)),
                        new UpdateOptions().upsert(true));

                // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?
                return result.getModifiedCount() == 1;
            } catch (MongoException e) {
                if (!isDuplicateKeyException(e)) {
                    throw e;
                }
            }

            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static void createNewSession(Start start, String sessionHandle, String userId, String refreshTokenHash2,
            JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT, long createdAtTime, boolean useStaticKey) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        collection.insertOne(new Document("_id", sessionHandle).append("user_id", userId)
                .append("refresh_token_hash_2", refreshTokenHash2).append("session_data", userDataInDatabase.toString())
                .append("expires_at", expiry).append("jwt_user_payload", userDataInJWT.toString())
                .append("created_at_time", createdAtTime).append("last_updated_sign", Utils.getUUID())
                .append("use_static_key", useStaticKey));
    }

    static SessionInfoWithLastUpdated getSessionInfo_Transaction(Start start, String sessionHandle)
            throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document result = (Document) collection.find(Filters.eq("_id", sessionHandle)).first();
        if (result == null) {
            return null;
        }

        return SessionInfoLastUpdatedRowMapper.getInstance().mapOrThrow(result);
    }

    static boolean updateSessionInfo_Transaction(Start start, String sessionHandle, String refreshTokenHash2,
            long expiry, String lastUpdatedSign) throws StorageQueryException {

        if (lastUpdatedSign == null) {
            throw new StorageQueryException(new Exception("lastUpdatedSign cannot be null for this update operation"));
        }

        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document toUpdate = new Document("$set", new Document("refresh_token_hash_2", refreshTokenHash2)
                .append("expires_at", expiry).append("last_updated_sign", Utils.getUUID()));

        UpdateResult result = collection.updateOne(
                Filters.and(Filters.eq("_id", sessionHandle), Filters.eq("last_updated_sign", lastUpdatedSign)),
                toUpdate, new UpdateOptions().upsert(false));
        // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

        return result.getModifiedCount() == 1;
    }

    static int getNumberOfSessions(Start start) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        return Math.toIntExact(collection.countDocuments()); // this is only used in testing, so this is OK.
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

    static String[] getAllNonExpiredSessionHandlesForUser(Start start, String userId) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());
        List<String> temp = new ArrayList<>();
        try (MongoCursor cursor = collection
                .find(Filters.and(Filters.eq("user_id", userId), Filters.gte("expires_at", System.currentTimeMillis())))
                .iterator()) {
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

    static void deleteAllExpiredSessions(Start start) {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        collection.deleteMany(Filters.lte("expires_at", System.currentTimeMillis()));
    }

    static SessionInfo getSession(Start start, String sessionHandle) throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document result = (Document) collection.find(Filters.eq("_id", sessionHandle)).first();
        if (result == null) {
            return null;
        }
        return SessionInfoRowMapper.getInstance().mapOrThrow(result);
    }

    static int updateSession(Start start, String sessionHandle, @Nullable JsonObject sessionData,
            @Nullable JsonObject jwtData) throws StorageQueryException {

        if (sessionData == null && jwtData == null) {
            throw new StorageQueryException(new Exception("sessionData and jwtData are null"));
        }

        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getSessionInfoCollection());

        Document updated = new Document("last_updated_sign", Utils.getUUID());
        if (sessionData != null) {
            updated.append("session_data", sessionData.toString());
        }
        if (jwtData != null) {
            updated.append("jwt_user_payload", jwtData.toString());
        }

        UpdateResult result = collection.updateOne(Filters.eq("_id", sessionHandle), new Document("$set", updated),
                new UpdateOptions().upsert(false));
        // TODO: supposed to call this only if result.wasAcknowledged() is true. Why?

        return result.getModifiedCount() == 1 ? 1 : 0;
    }

    private static class SessionInfoRowMapper implements RowMapper<SessionInfo, Document> {
        private static final SessionInfoRowMapper INSTANCE = new SessionInfoRowMapper();

        private SessionInfoRowMapper() {
        }

        private static SessionInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public SessionInfo map(Document result) throws Exception {
            JsonParser jp = new JsonParser();
            return new SessionInfo(result.getString("_id"), result.getString("user_id"),
                    result.getString("refresh_token_hash_2"),
                    jp.parse(result.getString("session_data")).getAsJsonObject(), result.getLong("expires_at"),
                    jp.parse(result.getString("jwt_user_payload")).getAsJsonObject(),
                    result.getLong("created_at_time"),
                    Boolean.TRUE.equals(result.getBoolean("use_static_key")));
        }
    }

    private static class KeyValueInfoLastUpdatedRowMapper implements RowMapper<KeyValueInfoWithLastUpdated, Document> {
        private static final KeyValueInfoLastUpdatedRowMapper INSTANCE = new KeyValueInfoLastUpdatedRowMapper();

        private KeyValueInfoLastUpdatedRowMapper() {
        }

        private static KeyValueInfoLastUpdatedRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public KeyValueInfoWithLastUpdated map(Document result) throws Exception {
            return new KeyValueInfoWithLastUpdated(result.getString("value"), result.getLong("created_at_time"),
                    result.getString("last_updated_sign"));
        }
    }

    private static class SessionInfoLastUpdatedRowMapper implements RowMapper<SessionInfoWithLastUpdated, Document> {
        private static final SessionInfoLastUpdatedRowMapper INSTANCE = new SessionInfoLastUpdatedRowMapper();

        private SessionInfoLastUpdatedRowMapper() {
        }

        private static SessionInfoLastUpdatedRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public SessionInfoWithLastUpdated map(Document result) throws Exception {
            JsonParser jp = new JsonParser();
            return new SessionInfoWithLastUpdated(result.getString("_id"), result.getString("user_id"),
                    result.getString("refresh_token_hash_2"),
                    jp.parse(result.getString("session_data")).getAsJsonObject(), result.getLong("expires_at"),
                    jp.parse(result.getString("jwt_user_payload")).getAsJsonObject(), result.getLong("created_at_time"),
                    Boolean.TRUE.equals(result.getBoolean("use_static_key")),
                    result.getString("last_updated_sign"));
        }
    }

    private static class KeyValueInfoArrayRowMapper implements RowMapper<List<KeyValueInfo>, Document> {
        private static final KeyValueInfoArrayRowMapper INSTANCE = new KeyValueInfoArrayRowMapper();

        private KeyValueInfoArrayRowMapper() {
        }

        private static KeyValueInfoArrayRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public List<KeyValueInfo> map(Document result) throws Exception {
            return result.getList("keys", Document.class).stream().map(
                    (Document subDoc) -> new KeyValueInfo(subDoc.getString("value"), subDoc.getLong("created_at_time")))
                    .collect(Collectors.toList());
        }
    }
}
