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
@ToString
@EqualsAndHashCode(of = {"height"})
public class BtcBlock {

    private final int height;
    private final BlockID hash;
    private final int txnCount;

    @Builder
    public BtcBlock(int height, byte[] hash, int txnCount) {
        this.height = height;
        this.hash = hash == null ? null : new BlockID(hash);
        this.txnCount = txnCount;
    }
}
