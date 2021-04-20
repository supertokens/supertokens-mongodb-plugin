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

@JsonIgnoreProperties(ignoreUnknown = true)
public class MongoDBConfig {

    @JsonProperty
    private int mongodb_config_version = -1;

    @JsonProperty
    private String mongodb_connection_uri = null;

    @JsonProperty
    private String mongodb_database_name = "supertokens";

    @JsonProperty
    private String mongodb_key_value_collection_name = null;

    @JsonProperty
    private String mongodb_session_info_collection_name = null;

    @JsonProperty
    private String mongodb_collection_names_prefix = "";

    public String getConnectionURI() {
        return mongodb_connection_uri;
    }

    public String getDatabaseName() {
        return mongodb_database_name;
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

    private String addPrefixToTableName(String tableName) {
        if (!mongodb_collection_names_prefix.trim().equals("")) {
            return mongodb_collection_names_prefix.trim() + "_" + tableName;
        }
        return tableName;
    }

    void validateAndInitialise() {
        if (getConnectionURI() == null) {
            throw new QuitProgramFromPluginException(
                    "'mongodb_connection_uri' is not set in the config.yaml file. Please set this value and restart " +
                            "SuperTokens");
        }
    }

}