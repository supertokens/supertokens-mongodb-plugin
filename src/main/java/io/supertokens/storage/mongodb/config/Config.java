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

package io.supertokens.storage.mongodb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonObject;
import io.supertokens.pluginInterface.LOG_LEVEL;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storage.mongodb.ResourceDistributor;
import io.supertokens.storage.mongodb.Start;
import io.supertokens.storage.mongodb.output.Logging;

import java.io.IOException;
import java.util.Set;

public class Config extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.storage.mongodb.config.Config";
    private final MongoDBConfig config;
    private final Start start;
    private Set<LOG_LEVEL> logLevels;

    private Config(Start start, JsonObject configJson, Set<LOG_LEVEL> logLevels) throws InvalidConfigException {
        this.start = start;
        this.logLevels = logLevels;
        try {
            config = loadMongoDBConfig(configJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Config getInstance(Start start) {
        return (Config) start.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void loadConfig(Start start, JsonObject configJson, Set<LOG_LEVEL> logLevels,
                                  TenantIdentifier tenantIdentifier) throws
            InvalidConfigException {
        if (getInstance(start) != null) {
            return;
        }
        start.getResourceDistributor().setResource(RESOURCE_KEY, new Config(start, configJson, logLevels));
        Logging.info(start, "Loading MongoDB config.", true);
    }

    public static Set<LOG_LEVEL> getLogLevels(Start start) {
        return getInstance(start).logLevels;
    }

    public static MongoDBConfig getConfig(Start start) {
        if (getInstance(start) == null) {
            throw new RuntimeException("Please call loadConfig() before calling getConfig()");
        }
        return getInstance(start).config;
    }

    public static void setLogLevels(Start start, Set<LOG_LEVEL> logLevels) {
        getInstance(start).logLevels = logLevels;
    }

    private MongoDBConfig loadMongoDBConfig(JsonObject configJson) throws IOException, InvalidConfigException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        MongoDBConfig config = mapper.readValue(configJson.toString(), MongoDBConfig.class);

        config.validateAndInitialise();
        return config;
    }

    public static boolean canBeUsed(JsonObject configJson) {
        try {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            MongoDBConfig config = mapper.readValue(configJson.toString(), MongoDBConfig.class);
            return config.getConnectionURI() != null;
        } catch (Exception e) {
            return false;
        }
    }

}
