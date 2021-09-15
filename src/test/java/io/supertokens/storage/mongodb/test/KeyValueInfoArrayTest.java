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
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.accessToken.AccessTokenSigningKey.KeyInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class KeyValueInfoArrayTest {
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
    public void checkThatAddWorksWorksWithNullLastCreated()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            UnsupportedEncodingException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        if (sessionStorage.getType() != STORAGE_TYPE.NOSQL_1) {
            return;
        }
        SessionNoSQLStorage_1 noSQLSessionStorage_1 = (SessionNoSQLStorage_1) sessionStorage;
        
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key1", 100), null);

        assertFalse(noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key3", 200), 101L));
        assertFalse(noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key3", 200), null));
        
        KeyValueInfo[] allKeys = noSQLSessionStorage_1.getAccessTokenSigningKeys_Transaction();
        assertEquals(allKeys.length, 1);
    }

    @Test
    public void checkThatAddWorksWorksWithWrongLastCreated()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            UnsupportedEncodingException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        if (sessionStorage.getType() != STORAGE_TYPE.NOSQL_1) {
            return;
        }
        SessionNoSQLStorage_1 noSQLSessionStorage_1 = (SessionNoSQLStorage_1) sessionStorage;
        
        assertFalse(noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key3", 200), 101L));
        assertEquals(noSQLSessionStorage_1.getAccessTokenSigningKeys_Transaction().length, 0);

        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key1", 100), null);
        assertFalse(noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key3", 200), null));
        assertEquals(noSQLSessionStorage_1.getAccessTokenSigningKeys_Transaction().length, 1);
    }

    @Test
    public void checkThatAddWorksWithCorrectLastCreated()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            UnsupportedEncodingException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        if (sessionStorage.getType() != STORAGE_TYPE.NOSQL_1) {
            return;
        }
        SessionNoSQLStorage_1 noSQLSessionStorage_1 = (SessionNoSQLStorage_1) sessionStorage;
        
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key1", 100), null);
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key2", 101), 100L);
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key3", 200), 101L);
        
        KeyValueInfo[] allKeys = noSQLSessionStorage_1.getAccessTokenSigningKeys_Transaction();
        assertEquals(allKeys.length, 3);

        assertEquals(allKeys[0].value, "key3");
        assertEquals(allKeys[1].value, "key2");
        assertEquals(allKeys[2].value, "key1");

        assertEquals(allKeys[0].createdAtTime, 200);
        assertEquals(allKeys[1].createdAtTime, 101);
        assertEquals(allKeys[2].createdAtTime, 100);
    }

    @Test
    public void checkRemoveAccessTokenSigningKeysBefore()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            UnsupportedEncodingException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        if (sessionStorage.getType() != STORAGE_TYPE.NOSQL_1) {
            return;
        }
        SessionNoSQLStorage_1 noSQLSessionStorage_1 = (SessionNoSQLStorage_1) sessionStorage;
        
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key1", 100), null);
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key2", 101), 100L);
        noSQLSessionStorage_1.addAccessTokenSigningKey_Transaction(new KeyValueInfo("key3", 200), 101L);

        noSQLSessionStorage_1.removeAccessTokenSigningKeysBefore(199);
        
        KeyValueInfo[] cleanedKeys = noSQLSessionStorage_1.getAccessTokenSigningKeys_Transaction();
        assertEquals(cleanedKeys.length, 1);
        assertEquals(cleanedKeys[0].value, "key3");
        assertEquals(cleanedKeys[0].createdAtTime, 200);
    }

}
