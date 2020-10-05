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

import ch.qos.logback.classic.Logger;
import com.google.gson.JsonObject;
import com.mongodb.MongoException;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storage.mongodb.config.Config;
import io.supertokens.storage.mongodb.output.Logging;
import org.slf4j.LoggerFactory;

public class Start extends NoSQLStorage_1 {

    private static final Object appenderLock = new Object();
    public static boolean silent = false;
    private ResourceDistributor resourceDistributor = new ResourceDistributor();
    private String processId;
    private MongoDBLoggingAppender appender = new MongoDBLoggingAppender(this);
    private static final String APP_ID_KEY_NAME = "app_id";
    private static final String ACCESS_TOKEN_SIGNING_KEY_NAME = "access_token_signing_key";
    private static final String REFRESH_TOKEN_KEY_NAME = "refresh_token_key";
    public static boolean isTesting = false;
    boolean enabled = true;
    Thread mainThread = Thread.currentThread();
    private Thread shutdownHook;

    @Override
    public void deleteAllInformation() throws StorageQueryException {
        try {
            Queries.deleteAllCollections(this);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void close() {
        ConnectionPool.close(this);
    }

    @Override
    public void setStorageLayerEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public SQLStorage.SessionInfo getSession(String sessionHandle) throws StorageQueryException {
        try {
            return Queries.getSession(this, sessionHandle);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int updateSession(String sessionHandle, JsonObject sessionData, JsonObject jwtPayload)
            throws StorageQueryException {
        try {
            return Queries.updateSession(this, sessionHandle, sessionData, jwtPayload);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteAllExpiredSessions() throws StorageQueryException {
        try {
            Queries.deleteAllExpiredSessions(this);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public String[] getAllSessionHandlesForUser(String userId) throws StorageQueryException {
        try {
            return Queries.getAllSessionHandlesForUser(this, userId);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int deleteSession(String[] sessionHandles) throws StorageQueryException {
        try {
            return Queries.deleteSession(this, sessionHandles);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int getNumberOfSessions() throws StorageQueryException {
        try {
            return Queries.getNumberOfSessions(this);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }


    @Override
    public boolean updateSessionInfo_Transaction(String sessionHandle,
                                                 String refreshTokenHash2, long expiry, String lastUpdatedSign)
            throws StorageQueryException {
        try {
            return Queries
                    .updateSessionInfo_Transaction(this, sessionHandle, refreshTokenHash2, expiry, lastUpdatedSign);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }


    @Override
    public SessionInfoWithLastUpdated getSessionInfo_Transaction(String sessionHandle)
            throws StorageQueryException {
        try {
            return Queries.getSessionInfo_Transaction(this, sessionHandle);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void createNewSession(String sessionHandle, String userId, String refreshTokenHash2,
                                 JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT,
                                 long createdAtTime)
            throws StorageQueryException {
        try {
            Queries.createNewSession(this, sessionHandle, userId, refreshTokenHash2, userDataInDatabase, expiry,
                    userDataInJWT, createdAtTime);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfoWithLastUpdated getRefreshTokenSigningKey_Transaction() throws
            StorageQueryException {
        return getKeyValue_Transaction(REFRESH_TOKEN_KEY_NAME);
    }

    @Override
    public boolean setRefreshTokenSigningKey_Transaction(KeyValueInfoWithLastUpdated info)
            throws StorageQueryException {
        return setKeyValue_Transaction(REFRESH_TOKEN_KEY_NAME, info);
    }

    @Override
    public KeyValueInfoWithLastUpdated getAccessTokenSigningKey_Transaction() throws StorageQueryException {
        return getKeyValue_Transaction(ACCESS_TOKEN_SIGNING_KEY_NAME);
    }

    @Override
    public boolean setAccessTokenSigningKey_Transaction(KeyValueInfoWithLastUpdated info)
            throws StorageQueryException {
        return setKeyValue_Transaction(ACCESS_TOKEN_SIGNING_KEY_NAME, info);
    }

    public ResourceDistributor getResourceDistributor() {
        return resourceDistributor;
    }

    public String getProcessId() {
        return this.processId;
    }

    @Override
    public void constructor(String processId, boolean silent) {
        this.processId = processId;
        Start.silent = silent;
    }

    @Override
    public STORAGE_TYPE getType() {
        return STORAGE_TYPE.NOSQL_1;
    }

    @Override
    public void loadConfig(String configFilePath) {
        Config.loadConfig(this, configFilePath);
    }

    @Override
    public void initFileLogging(String infoLogPath, String errorLogPath) {
        Logging.initFileLogging(this, infoLogPath, errorLogPath);

        /*
         * NOTE: The log this produces is only accurate in production or development.
         *
         * For testing, it may happen that multiple processes are running at the same
         * time which can lead to one of them being the winner and its start instance
         * being attached to logger class. This would yield inaccurate processIds during
         * logging.
         *
         * Finally, during testing, the winner's logger might be removed, in which case
         * nothing will be handling logging and mongodb's logs would not be outputed
         * anywhere.
         */
        synchronized (appenderLock) {
            final Logger infoLog = (Logger) LoggerFactory.getLogger("org.mongodb.driver");
            if (infoLog.getAppender(MongoDBLoggingAppender.NAME) == null) {
                infoLog.setAdditive(false);
                infoLog.addAppender(appender);
            }
        }

    }

    @Override
    public void stopLogging() {
        Logging.stopLogging(this);

        synchronized (appenderLock) {
            final Logger infoLog = (Logger) LoggerFactory.getLogger("org.mongodb.driver");
            if (infoLog.getAppender(MongoDBLoggingAppender.NAME) != null) {
                infoLog.detachAppender(MongoDBLoggingAppender.NAME);
            }
        }
    }

    @Override
    public void setAppId(String appId) throws StorageQueryException {
        KeyValueInfo keyInfo = new KeyValueInfo(appId, System.currentTimeMillis());
        setKeyValue(APP_ID_KEY_NAME, keyInfo);
    }

    @Override
    public void initStorage() {
        ConnectionPool.initPool(this);
    }

    @Override
    public String getAppId() throws StorageQueryException {
        KeyValueInfo result = getKeyValue(APP_ID_KEY_NAME);
        if (result != null) {
            return result.value;
        }
        return null;
    }

    @Override
    public void setKeyValue(String key, KeyValueInfo info) throws StorageQueryException {
        try {
            Queries.setKeyValue(this, key, info);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfo getKeyValue(String key) throws StorageQueryException {
        try {
            return Queries.getKeyValue(this, key);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public boolean setKeyValue_Transaction(String key, KeyValueInfoWithLastUpdated info)
            throws StorageQueryException {
        try {
            return Queries.setKeyValue_Transaction(this, key, info);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfoWithLastUpdated getKeyValue_Transaction(String key) throws StorageQueryException {
        try {
            return Queries.getKeyValue_Transaction(this, key);
        } catch (MongoException e) {
            throw new StorageQueryException(e);
        }
    }

    void removeShutdownHook() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                shutdownHook = null;
            } catch (IllegalStateException ignored) {
            }
        }
    }

    void handleKillSignalForWhenItHappens() {
        if (shutdownHook != null) {
            return;
        }
        shutdownHook = new Thread(() -> {
            mainThread.interrupt();
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public boolean canBeUsed(String configFilePath) {
        return Config.canBeUsed(configFilePath);
    }

}
