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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.supertokens.pluginInterface.exceptions.QuitProgramFromPluginException;

import java.net.URI;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MongoDBConfig {

    @JsonProperty
    private int mongodb_config_version = -1;

    @JsonProperty
    private String mongodb_connection_uri = null;

    @JsonProperty
    private String mongodb_database_name = null;

    @JsonProperty
    private String mongodb_key_value_collection_name = null;

    @JsonProperty
    private String mongodb_session_info_collection_name = null;

    @JsonProperty
    private String mongodb_collection_names_prefix = "";

    public boolean useConnectionURIAsIs() {
        return mongodb_database_name == null && mongodb_connection_uri != null;
    }

    public String getConnectionScheme() {
        URI uri = URI.create(mongodb_connection_uri);

        // sometimes if the scheme is missing, the host is returned as the scheme. To prevent that,
        // we have a check
        String host = this.getHostName();
        if (uri.getScheme() != null && !uri.getScheme().equals(host)) {
            return uri.getScheme();
        }
        return "mongodb";
    }

    public String getConnectionAttributes() {
        URI uri = URI.create(mongodb_connection_uri);
        String query = uri.getQuery();
        if (query != null) {
            return query;
        }
        return "";
    }

    public String getHostName() {
        URI uri = URI.create(mongodb_connection_uri);
        if (uri.getHost() != null) {
            return uri.getHost();
        }
        return "localhost";
    }

    public int getPort() {
        URI uri = URI.create(mongodb_connection_uri);
        return uri.getPort();
    }

    public String getUser() {
        URI uri = URI.create(mongodb_connection_uri);
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] userInfoArray = userInfo.split(":");
            if (userInfoArray.length > 0 && !userInfoArray[0].equals("")) {
                return userInfoArray[0];
            }
        }
        return null;
    }

    public String getPassword() {
        URI uri = URI.create(mongodb_connection_uri);
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] userInfoArray = userInfo.split(":");
            if (userInfoArray.length > 1 && !userInfoArray[1].equals("")) {
                return userInfoArray[1];
            }
        }
        return null;
    }

    public String getDatabaseName() {
        if (mongodb_database_name == null) {
            URI uri = URI.create(mongodb_connection_uri);
            String path = uri.getPath();
            if (path != null && !path.equals("") && !path.equals("/")) {
                if (path.startsWith("/")) {
                    return path.substring(1);
                }
                return path;
            }
            return "supertokens";
        }
        return mongodb_database_name;
    }

    public String getConnectionURI() {
        return mongodb_connection_uri;
    }

    public String getKeyValueCollection() {
        String tableName = "key_value";
        if (mongodb_key_value_collection_name != null) {
            return mongodb_key_value_collection_name;
        }
        return addPrefixToTableName(tableName);
    }

    public String getSessionInfoCollection() {
        String tableName = "session_info";
        if (mongodb_session_info_collection_name != null) {
            return mongodb_session_info_collection_name;
        }
        return addPrefixToTableName(tableName);
    }

    public String getJWTSigningKeysCollection() {
        return addPrefixToTableName("jwt_signing_keys");
    }

    private String addPrefixToTableName(String tableName) {
        if (!mongodb_collection_names_prefix.trim().equals("")) {
            return mongodb_collection_names_prefix.trim() + "_" + tableName;
        }
        return tableName;
    }

    void validateAndInitialise() {

        if (mongodb_connection_uri == null) {
            throw new QuitProgramFromPluginException(
                    "'mongodb_connection_uri' is not set in the config.yaml file. Please set this value and restart "
                            + "SuperTokens");
        }

        try {
            URI ignored = URI.create(mongodb_connection_uri);
        } catch (Exception e) {
            throw new QuitProgramFromPluginException(
                    "The provided mongodb connection URI has an incorrect format. Please use a format like "
                            + "mongodb+srv://[user[:[password]]@]host[:port][/dbname][?attr1=val1&attr2=val2...");
        }
    }

}