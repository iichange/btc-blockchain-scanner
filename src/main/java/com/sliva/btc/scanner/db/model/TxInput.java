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
package com.sliva.btc.scanner.db.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 *
 * @author Sliva Co
 */
@Getter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode(of = {"transactionId", "pos"})
public class TxInput implements Comparable<TxInput> {

    private final int transactionId;
    private final short pos;
    private final int inTransactionId;
    private final short inPos;

    @Override
    public int compareTo(TxInput o) {
        int c = Integer.compare(transactionId, o.transactionId);
        return c != 0 ? c : Integer.compare(pos, o.pos);
    }
}
