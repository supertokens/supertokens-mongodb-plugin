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

package io.supertokens.storage.mongodb.test;

import io.supertokens.ProcessState;
import io.supertokens.storage.mongodb.ConnectionPoolTestContent;
import io.supertokens.storage.mongodb.Start;
import io.supertokens.storage.mongodb.config.Config;
import io.supertokens.storage.mongodb.config.MongoDBConfig;
import io.supertokens.storageLayer.StorageLayer;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testThatDefaultConfigLoadsCorrectly() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorageLayer(process.getProcess()));

        checkConfig(config);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatCustomConfigLoadsCorrectly() throws Exception {
        String[] args = {"../", "DEV"};

        Utils.setValueInConfig("mongodb_past_tokens_collection_name", "\"temp_name\"");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorageLayer(process.getProcess()));
        assertEquals(config.getPastTokensCollection(), "temp_name");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatInvalidConfigThrowsRightError() throws Exception {
        String[] args = {"../", "PRODUCTION"};

        //'mongodb_connection_uri is not set properly in the config file

        Utils.commentConfigValue("mongodb_connection_uri");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        TestCase.assertEquals(e.exception.getMessage(),
                "'mongodb_connection_uri' is not set in the config.yaml file. Please set this value and restart " +
                        "SuperTokens");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.reset();

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));


    }

    @Test
    public void testThatMissingConfigFileThrowsError() throws Exception {
        String[] args = {"../", "DEV"};

        ProcessBuilder pb = new ProcessBuilder("rm", "-r", "config.yaml");
        pb.directory(new File(args[0]));
        Process process1 = pb.start();
        process1.waitFor();

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        TestCase.assertEquals(e.exception.getMessage(),
                "java.io.FileNotFoundException: ../config.yaml (No such file or directory)");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));


    }

    @Test
    public void testCustomLocationForConfigLoadsCorrectly() throws Exception {
        String[] args = {"../", "DEV", "configFile=../temp/config.yaml"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        TestCase.assertEquals(e.exception.getMessage(), "configPath option must be an absolute path only");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        //absolute path
        File f = new File("../temp/config.yaml");
        args = new String[]{"../", "DEV", "configFile=" + f.getAbsolutePath()};

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorageLayer(process.getProcess()));
        checkConfig(config);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadPortInput() throws Exception {
        String[] args = {"../", "DEV"};

        Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@localhost:27018");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().waitToInitStorageModule();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.WAITING_TO_INIT_STORAGE_MODULE));

        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorageLayer(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.TIME_TO_WAIT_TO_INIT, 5000);
        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorageLayer(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.RETRY_INTERVAL_IF_INIT_FAILS, 2000);
        process.getProcess().proceedWithInitingStorageModule();

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE, 7000);
        assertNotNull(e);
        assertEquals(e.exception.getMessage(),
                "Error connecting to MongoDB instance. Please make sure that MongoDB is running and that you have " +
                        "specified the correct value for 'mongodb_connection_uri' in your config file");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void storageDisabledAndThenEnabled() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().waitToInitStorageModule();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.WAITING_TO_INIT_STORAGE_MODULE));

        StorageLayer.getStorageLayer(process.getProcess()).setStorageLayerEnabled(false);
        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorageLayer(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.TIME_TO_WAIT_TO_INIT, 10000);
        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorageLayer(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.RETRY_INTERVAL_IF_INIT_FAILS, 2000);
        process.getProcess().proceedWithInitingStorageModule();

        Thread.sleep(5000);
        StorageLayer.getStorageLayer(process.getProcess()).setStorageLayerEnabled(true);

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadHostInput() throws Exception {
        String[] args = {"../", "DEV"};

        Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@random:27017");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);

        assertEquals(
                "Timed out after 5000 ms while waiting to connect. Client view of cluster state is {type=UNKNOWN, " +
                        "servers=[{address=random:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb" +
                        ".MongoSocketException: random}, caused by {java.net.UnknownHostException: random}}]",
                e.exception.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatChangeInCollectionNameIsCorrect() throws Exception {
        String[] args = {"../", "DEV"};

        Utils.setValueInConfig("mongodb_key_value_collection_name", "key_value_collection");
        Utils.setValueInConfig("mongodb_session_info_collection_name", "session_info_collection");
        Utils.setValueInConfig("mongodb_past_tokens_collection_name", "past_tokens_collection");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorageLayer(process.getProcess()));

        assertEquals("change in PastTokensCollection name not reflected", config.getPastTokensCollection(),
                "past_tokens_collection");
        assertEquals("change in KeyValueCollection name not reflected", config.getKeyValueCollection(),
                "key_value_collection");
        assertEquals("change in SessionInfoCollection name not reflected", config.getSessionInfoCollection(),
                "session_info_collection");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void checkConfig(MongoDBConfig config) {

        assertEquals("Config connectionPoolSize did not match default", config.getConnectionURI(),
                "mongodb://root:root@localhost:27017");
        assertEquals("Config databaseName does not match default", config.getDatabaseName(), "auth_session");
        assertEquals("Config keyValue collection does not match default", config.getKeyValueCollection(), "key_value");
        assertEquals("Config pastTokensCollection does not match default", config.getPastTokensCollection(),
                "past_tokens");
        assertEquals("Config sessionInfoCollection does not match default", config.getSessionInfoCollection(),
                "session_info");
    }

}
