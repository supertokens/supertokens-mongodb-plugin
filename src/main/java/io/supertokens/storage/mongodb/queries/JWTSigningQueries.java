/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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
 */

package io.supertokens.storage.mongodb.queries;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSymmetricSigningKeyInfo;
import io.supertokens.storage.mongodb.ConnectionPool;
import io.supertokens.storage.mongodb.Start;
import io.supertokens.storage.mongodb.config.Config;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class JWTSigningQueries {
    public static List<JWTSigningKeyInfo> getJWTSigningKeys_Transaction(Start start) throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getJWTSigningKeysCollection());
        List<JWTSigningKeyInfo> result = new ArrayList<>();

        /*
         * Schema for the collection is
         * {
         * _id: string, (key id)
         * key_string: string,
         * created_at: long,
         * algorithm: string,
         * }
         *
         * created_at should only be used to determine the key that was added to the database last, it should not be
         * used to determine the validity or lifetime of a key. While the assumption that created_at refers to the time
         * the key was generated holds true for keys generated by the core, it is not guaranteed when we allow user
         * defined
         * keys in the future.
         */
        try (MongoCursor cursor = collection.find().sort(Sorts.descending("created_at")).iterator()) {
            while (cursor.hasNext()) {
                Document currentDoc = (Document) cursor.next();
                result.add(JWTSigningKeyInfoRowMapper.getInstance().mapOrThrow(currentDoc));
            }
        }

        return result;
    }

    private static class JWTSigningKeyInfoRowMapper implements RowMapper<JWTSigningKeyInfo, Document> {
        private static final JWTSigningKeyInfoRowMapper INSTANCE = new JWTSigningKeyInfoRowMapper();

        private JWTSigningKeyInfoRowMapper() {
        }

        private static JWTSigningKeyInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public JWTSigningKeyInfo map(Document result) throws Exception {
            String keyId = result.getString("_id");
            String keyString = result.getString("key_string");
            long createdAt = result.getLong("created_at");
            String algorithm = result.getString("algorithm");

            if (keyString.contains("|") || keyString.contains(";")) {
                return new JWTAsymmetricSigningKeyInfo(keyId, createdAt, algorithm, keyString);
            } else {
                return new JWTSymmetricSigningKeyInfo(keyId, createdAt, algorithm, keyString);
            }
        }
    }

    public static boolean setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(Start start,
                                                                                    JWTSigningKeyInfo keyInfo)
            throws StorageQueryException {
        MongoDatabase client = ConnectionPool.getClientConnectedToDatabase(start);
        MongoCollection collection = client.getCollection(Config.getConfig(start).getJWTSigningKeysCollection());

        Document toInsertIfNoneFound = new Document("$setOnInsert",
                new Document("_id", keyInfo.keyId).append("key_string", keyInfo.keyString)
                        .append("created_at", keyInfo.createdAtTime).append("algorithm", keyInfo.algorithm));

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.upsert(true);

        /*
         * findOneAndUpdate will try to find a row with the matching filter and try to insert if one isn't found.
         * This means that when setting Key2 when Key1 (with a matching algorithm) already exists in storage, the query
         * will
         * return Key1. So after the query we need to compare the returned document with the one we were trying to set
         * to determine
         * if the set query succeeded.
         */
        Document result = (Document) collection.findOneAndUpdate(Filters.eq("algorithm", keyInfo.algorithm),
                toInsertIfNoneFound, options);

        /*
         * Because we use findOneAndUpdate we cannot solely rely on key id to determine if a document was inserted or
         * not
         * For example: Consider that storage has a key (alg: RSA, keyId: 123, keyString: 1234)
         * and at some point a write is made to set a key (alg: RSA, keyId: 123, keyString: 5678) [In theory this should
         * never happen for keys generated from within the core, but in the future when we allow user defined keys this
         * may be a situation]
         *
         * In this case comparing just key ids would return true when it shouldn't, and the caller will proceed to use a
         * different key string than the one in storage. To resolve this we will first convert the Document to the key
         * class
         * and do a full equals check instead
         */
        return JWTSigningKeyInfoRowMapper.getInstance().mapOrThrow(result).equals(keyInfo);
    }
}
