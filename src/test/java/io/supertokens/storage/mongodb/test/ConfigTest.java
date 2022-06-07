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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
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

import static org.junit.Assert.*;

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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));

        checkConfig(config);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatMongoSRVConnectionURIWorksCorrectly() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_connection_uri",
                "\"mongodb+srv://root:root@cluster0.zh79vjj.mongodb.net/myFirstDatabase?retryWrites=true&w=majority\"");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatMongoOldStyleWithClusterConnectionURIWorksCorrectly() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_connection_uri",
                "\"mongodb://root:root@ac-fwlbhn2-shard-00-00.zh79vjj.mongodb.net:27017,ac-fwlbhn2-shard-00-01"
                        + ".zh79vjj.mongodb.net:27017,ac-fwlbhn2-shard-00-02.zh79vjj.mongodb"
                        + ".net:27017/?ssl=true&replicaSet=atlas-jjdnxo-shard-0&authSource=admin&retryWrites=true&w"
                        + "=majority\"");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatCustomConfigLoadsCorrectly() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_key_value_collection_name", "\"temp_name\"");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
        assertEquals(config.getKeyValueCollection(), "temp_name");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatMissingConfigFileThrowsError() throws Exception {
        String[] args = { "../" };

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
        String[] args = { "../", "configFile=../temp/config.yaml" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        TestCase.assertEquals(e.exception.getMessage(), "configPath option must be an absolute path only");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        // absolute path
        File f = new File("../temp/config.yaml");
        args = new String[] { "../", "configFile=" + f.getAbsolutePath() };

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
        checkConfig(config);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadPortInput() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@localhost:27018");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().waitToInitStorageModule();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.WAITING_TO_INIT_STORAGE_MODULE));

        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorage(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.TIME_TO_WAIT_TO_INIT, 5000);
        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorage(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.RETRY_INTERVAL_IF_INIT_FAILS, 2000);
        process.getProcess().proceedWithInitingStorageModule();

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE, 7000);
        assertNotNull(e);
        assertEquals(e.exception.getMessage(),
                "Error connecting to MongoDB instance. Please make sure that MongoDB is running and that you have "
                        + "specified the correct value for 'mongodb_connection_uri' in your config file");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void storageDisabledAndThenEnabled() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().waitToInitStorageModule();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.WAITING_TO_INIT_STORAGE_MODULE));

        StorageLayer.getStorage(process.getProcess()).setStorageLayerEnabled(false);
        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorage(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.TIME_TO_WAIT_TO_INIT, 10000);
        ConnectionPoolTestContent.getInstance((Start) StorageLayer.getStorage(process.getProcess()))
                .setKeyValue(ConnectionPoolTestContent.RETRY_INTERVAL_IF_INIT_FAILS, 2000);
        process.getProcess().proceedWithInitingStorageModule();

        Thread.sleep(5000);
        StorageLayer.getStorage(process.getProcess()).setStorageLayerEnabled(true);

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadHostInput() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@random:27017");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);

        assertEquals(
                "Timed out after 5000 ms while waiting to connect. Client view of cluster state is {type=UNKNOWN, "
                        + "servers=[{address=random:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb"
                        + ".MongoSocketException: random}, caused by {java.net.UnknownHostException: random}}]",
                e.exception.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatChangeInCollectionNameIsCorrect() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_key_value_collection_name", "key_value_collection");
        Utils.setValueInConfig("mongodb_session_info_collection_name", "session_info_collection");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));

        assertEquals("change in KeyValueCollection name not reflected", config.getKeyValueCollection(),
                "key_value_collection");
        assertEquals("change in SessionInfoCollection name not reflected", config.getSessionInfoCollection(),
                "session_info_collection");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingTableNamePrefixWorks() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("mongodb_key_value_collection_name", "key_value_table");
        Utils.setValueInConfig("mongodb_collection_names_prefix", "some_prefix");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));

        assertEquals("change in KeyValueTable name not reflected", config.getKeyValueCollection(), "key_value_table");
        assertEquals("change in SessionInfoTable name not reflected", config.getSessionInfoCollection(),
                "some_prefix_session_info");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidConnectionURI() throws Exception {
        {
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@localhost:27017/supertokens");
            Utils.commentConfigValue("mongodb_database_name");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
            checkConfig(config);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.reset();
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@localhost/supertokens");
            Utils.commentConfigValue("mongodb_database_name");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
            assertEquals(config.getPort(), -1);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.reset();
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri", "mongodb://localhost:27017/supertokens");
            Utils.commentConfigValue("mongodb_database_name");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
            assertNull(config.getUser());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.reset();
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri", "mongodb://root:root@localhost:27017");
            Utils.commentConfigValue("mongodb_database_name");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
            checkConfig(config);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testInvalidConnectionURI() throws Exception {
        {
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri", ":/localhost:27017/supertokens");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(
                    "The provided mongodb connection URI has an incorrect format. Please use a format like "
                            + "mongodb+srv://[user[:[password]]@]host[:port][/dbname][?attr1=val1&attr2=val2...",
                    e.exception.getMessage());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void testValidConnectionURIAttributes() throws Exception {
        {
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri",
                    "mongodb://root:root@localhost:27017/supertokens?key1=value1");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
            assertEquals(config.getConnectionAttributes(), "key1=value1");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.reset();
            String[] args = { "../" };

            Utils.setValueInConfig("mongodb_connection_uri",
                    "mongodb://root:root@localhost:27017/supertokens?key1=value1&key2" + "=value2");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            MongoDBConfig config = Config.getConfig((Start) StorageLayer.getStorage(process.getProcess()));
            assertEquals(config.getConnectionAttributes(), "key1=value1&key2=value2");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    private static void checkConfig(MongoDBConfig config) {

        assertEquals("Config getAttributes did not match default", config.getConnectionAttributes(), "");
        assertEquals("Config getSchema did not match default", config.getConnectionScheme(), "mongodb");
        assertEquals("Config databaseName does not match default", config.getDatabaseName(), "supertokens");
        assertEquals("Config hostName does not match default ", config.getHostName(), "localhost");
        assertEquals("Config port does not match default", config.getPort(), 27017);
        assertEquals("Config user does not match default", config.getUser(), "root");
        assertEquals("Config password does not match default", config.getPassword(), "root");
        assertEquals("Config databaseName does not match default", config.getDatabaseName(), "supertokens");
        assertEquals("Config keyValue collection does not match default", config.getKeyValueCollection(), "key_value");
        assertEquals("Config sessionInfoCollection does not match default", config.getSessionInfoCollection(),
                "session_info");
    }

}
