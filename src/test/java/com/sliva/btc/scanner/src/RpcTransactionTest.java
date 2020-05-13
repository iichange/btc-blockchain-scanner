/*
 * Copyright 2018 Sliva Co.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sliva.btc.scanner.src;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.RawTransaction.In;

/**
 *
 * @author whost
 */
public class RpcTransactionTest {

    private static final String REAL_TXID = "fc3a8493cb1597423b212dcc4f25dbcbf0dd992aacdc528e16a8e47c2e6fc2aa";
    private static final RpcTransaction<?, ?> transactionZero = new RpcTransaction<>(RpcTransaction.TRANSACTION_ZERO_ID);
    private static final RpcTransaction<?, ?> realTxInstance = new RpcTransaction<>(REAL_TXID);

    public RpcTransactionTest() {
    }

    @BeforeClass
    public static void setUpClass() {
        com.sliva.btc.scanner.rpc.RpcSetup.init();
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getTxid method, of class RpcTransaction.
     */
    @Test
    public void testGetTxid() {
        System.out.println("getTxid");
        String expResult = RpcTransaction.TRANSACTION_ZERO_ID;
        String result = transactionZero.getTxid();
        assertEquals(expResult, result);
    }

    /**
     * Test of getInputs method, of class RpcTransaction.
     */
    @Test
    public void testGetInputs() {
        System.out.println("getInputs");
        Collection<RpcInput> expResult = Collections.emptyList();
        Collection<RpcInput> result = transactionZero.getInputs();
        assertEquals(expResult, result);
    }

    /**
     * Test of getOutputs method, of class RpcTransaction.
     */
    @Test
    public void testGetOutputs() {
        System.out.println("getOutputs");
        Collection<?> result = transactionZero.getOutputs();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    /**
     * Test of toString method, of class RpcTransaction.
     */
    @Test
    public void testToString() {
        System.out.println("toString");
        String result = transactionZero.toString();
        System.out.println("result=" + result);
        assertNotNull(result);
    }

    @Test
    public void testRetrieveRealRawTx() {
        Collection<RpcInput> inputs = realTxInstance.getInputs();
        System.out.println("inputs=" + inputs);
        assertNotNull(inputs);
        RpcInput rin = inputs.iterator().next();
        In in = rin.getIn();
        System.out.println("in=" + in);
        Map<String, Object> scriptSig = in.scriptSig();
        System.out.println("scriptSig=" + scriptSig);
    }
}
