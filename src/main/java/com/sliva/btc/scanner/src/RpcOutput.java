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

import lombok.ToString;
import org.bitcoinj.core.TransactionOutput;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.RawTransaction.Out;

/**
 *
 * @author Sliva Co
 * @param <A>
 */
@ToString
public class RpcOutput<A extends RpcAddress> implements SrcOutput<RpcAddress> {

    private final Out out;
    private final TransactionOutput txout;

    public RpcOutput(Out out) {
        this.out = out;
        this.txout = null;
    }

    public RpcOutput(TransactionOutput txout) {
        this.out = null;
        this.txout = txout;
    }

    @Override
    public short getPos() {
        return txout != null ? (short) txout.getIndex() : (short) out.n();
    }

    @Override
    public RpcAddress getAddress() {
        try {
            return RpcAddress.fromString(txout != null ? new BJOutput<>(txout).getAddress().getName() : out.scriptPubKey().addresses().get(0));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long getValue() {
        return txout != null ? txout.getValue().getValue() : out.value().movePointRight(8).longValueExact();
    }

}
