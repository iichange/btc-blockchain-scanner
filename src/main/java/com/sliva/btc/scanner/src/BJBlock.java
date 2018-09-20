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

import com.sliva.btc.scanner.util.BJBlockHandler;
import java.io.IOException;
import java.util.stream.Stream;
import lombok.ToString;
import org.bitcoinj.core.Block;

/**
 *
 * @author Sliva Co
 */
@ToString
public class BJBlock implements SrcBlock {

    private final Block block;
    private final int height;

    public BJBlock(String hash, int height) throws IOException {
        this.block = BJBlockHandler.getBlock(hash);
        this.height = height;
    }

    @Override
    public String getHash() {
        return block.getHashAsString();
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    @SuppressWarnings("null")
    public Stream<SrcTransaction> getTransactions() {
        return block.getTransactions().stream().map((t) -> new BJTransaction(t));
    }

}
