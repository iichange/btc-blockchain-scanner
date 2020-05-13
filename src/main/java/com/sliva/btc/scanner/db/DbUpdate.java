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
package com.sliva.btc.scanner.db;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import static com.sliva.btc.scanner.db.DBConnectionSupplier.dbConfigOpt;
import com.sliva.btc.scanner.db.DBPreparedStatement.ParamSetter;
import com.sliva.btc.scanner.util.BatchUtils;
import com.sliva.btc.scanner.util.CommandLineUtils;
import static com.sliva.btc.scanner.util.CommandLineUtils.buildOption;
import com.sliva.btc.scanner.util.LazyInitializer;
import com.sliva.btc.scanner.util.Utils;
import static com.sliva.btc.scanner.util.Utils.getPercentage;
import static com.sliva.btc.scanner.util.Utils.synchronize;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

/**
 *
 * @author Sliva Co
 */
@Slf4j
public abstract class DbUpdate implements AutoCloseable {

    private static final Duration PRINT_STATS_PERIOD = Duration.ofSeconds(30);
    private static final int MYSQL_BULK_INSERT_BUFFER_SIZE = 256 * 1024 * 1024;
    private static final int DEFAULT_DB_WRITE_THREADS = 4;

    public static final CommandLineUtils.CmdOptions CMD_OPTS = new CommandLineUtils.CmdOptions();
    public static final CommandLineUtils.CmdOption dbWriteThreadsOpt = buildOption(CMD_OPTS, null, "db-write-threads", true, "Number of DB write threads. Default: " + DEFAULT_DB_WRITE_THREADS);

    private static volatile ExecuteDbUpdate executeDbUpdateThread;
    private static final Collection<DbUpdate> dbUpdateInstances = new ArrayList<>();
    private static final Set<DbUpdate> executingInstances = new HashSet<>();
    private static final Map<String, ExecStats> execStats = new HashMap<>();
    private static final StopWatch lastPrintedTime = StopWatch.createStarted();
    private static int DB_WRITE_THREADS = DEFAULT_DB_WRITE_THREADS;
    private static LazyInitializer<ExecutorService> executor;

    @Getter
    @NonNull
    private final String tableName;
    @Getter
    private boolean isActive = true;
    protected final Object execSync = new Object();

    @SuppressWarnings({"LeakingThisInConstructor", "CallToThreadStartDuringObjectConstruction"})
    public DbUpdate(String tableName, DBConnectionSupplier conn) {
        checkArgument(tableName != null, "Argument 'tableName' is null");
        checkArgument(conn != null, "Argument 'conn' is null");
        this.tableName = tableName;
        try {
            conn.get().createStatement().execute("SET bulk_insert_buffer_size=" + MYSQL_BULK_INSERT_BUFFER_SIZE);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        synchronized (dbUpdateInstances) {
            dbUpdateInstances.add(this);
        }
        staticInit();
    }

    private static void staticInit() {
        synchronized (ExecuteDbUpdate.class) {
            if (executeDbUpdateThread == null) {
                executor = new LazyInitializer<>(() -> new ThreadPoolExecutor(DB_WRITE_THREADS, DB_WRITE_THREADS,
                        0L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(1),
                        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("DBWriteThread-%d").build()));
                ExecuteDbUpdate t = new ExecuteDbUpdate(executor);
                t.start();
                executeDbUpdateThread = t;
            }
        }
    }

    public void flushCache() {
        log.trace("flushCache() Called");
        while (executeSync() != 0) {
        }
    }

    @Override
    public void close() {
        log.debug("{}.close()", tableName);
        isActive = false;
        flushCache();
        synchronized (dbUpdateInstances) {
            dbUpdateInstances.remove(this);
        }
        log.trace("{}.close() FINISHED", tableName);
    }

    public abstract int getCacheFillPercent();

    public abstract boolean isExecuteNeeded();

    public abstract int executeInserts();

    public abstract int executeUpdates();

    @SneakyThrows(InterruptedException.class)
    protected static void waitFullQueue(Collection<?> queue, int maxQueueLength) {
        while (queue.size() >= maxQueueLength) {
            synchronized (queue) {
                queue.wait(100);
            }
        }
    }

    /**
     * Execute batch of statements.
     *
     * @param <T> Element type
     * @param syncObject Object to synchronize on when pulling data from source
     * Collection
     * @param source Source Collection
     * @param ps DB Statement to execute in batch
     * @param batchMaxSize Batch maximum size
     * @param fillCallback callback to fill each DB statement in batch
     * @param postExecutor post-execution process, can be null
     * @return number of records executed
     */
    public <T> int executeBatch(Object syncObject, Collection<T> source, DBPreparedStatement ps, int batchMaxSize, BiConsumer<T, ParamSetter> fillCallback, Consumer<Collection<T>> postExecutor) {
        checkArgument(syncObject != null, "Argument 'syncObject' is null");
        checkArgument(source != null, "Argument 'source' is null");
        checkArgument(batchMaxSize > 0, "Argument 'batchMaxSize' (%s) must be a positive number", batchMaxSize);
        checkArgument(ps != null, "Argument 'ps' is null");
        checkArgument(fillCallback != null, "Argument 'fillCallback' is null");
        Collection<T> batchToRun = synchronize(syncObject, () -> BatchUtils.pullData(source, batchMaxSize));
        if (batchToRun != null) {
            synchronized (execSync) {
                BatchExecutor.executeBatch(batchToRun, ps, fillCallback);
                if (postExecutor != null) {
                    postExecutor.accept(batchToRun);
                }
            }
        }
        return batchToRun == null ? 0 : batchToRun.size();
    }

    private int executeSync() {
        int nRecs = 0;
        try {
            log.trace("{}.executeSync(): STARTED", tableName);
            long s = System.nanoTime();
            try {
                nRecs = executeInserts();
                nRecs += executeUpdates();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            } finally {
                long runtimeNanos = System.nanoTime() - s;
                if (nRecs > 0) {
                    updateRuntimeMap(tableName, nRecs, runtimeNanos);
                    if (log.isTraceEnabled()) {
                        log.trace("{}.executeSync(): insert/update queries executed: {}. Runtime {} ms.",
                                tableName, nRecs, BigDecimal.valueOf(runtimeNanos).movePointLeft(6).setScale(3, RoundingMode.HALF_DOWN));
                    }
                }
            }
        } finally {
            synchronized (executingInstances) {
                executingInstances.remove(this);
            }
        }
        return nRecs;
    }

    private void executeAsync() {
        for (;;) {
            try {
                log.trace("{}.executeAsync(): Submitting ", tableName);
                synchronized (executingInstances) {
                    executor.get().submit(this::executeSync);
                    executingInstances.add(this);
                }
                log.trace("{}.executeAsync(): Submitted ", tableName);
                break;
            } catch (RejectedExecutionException e) {
                log.trace("Err: {}: {}", e.getClass(), e.getMessage());
                Utils.sleep(100);
            }
        }
    }

    private static void updateRuntimeMap(String tableName, long records, long runtimeNanos) {
        synchronized (execStats) {
            execStats.computeIfAbsent(tableName, ExecStats::new)
                    .addExecution(records, runtimeNanos);
        }
    }

    public static void printStats() {
        if (log.isDebugEnabled() && executeDbUpdateThread != null) {
            long runtimeInSec = Math.max(TimeUnit.NANOSECONDS.toSeconds(executeDbUpdateThread.startTime.getNanoTime()), 1);
            synchronized (execStats) {
                execStats.entrySet().forEach((e) -> {
                    ExecStats s = e.getValue();
                    log.debug("{}\t Executions: {},\t Records: {},\t speed: {} rec/sec,\t runtime: {}\t ({}%)",
                            StringUtils.rightPad(e.getKey(), 16),
                            s.getExecutions(),
                            s.getTotalRecords(),
                            s.getTotalRecords() / runtimeInSec,
                            Duration.ofMillis(TimeUnit.NANOSECONDS.toMillis(s.getTotalRuntimeNanos())),
                            getPercentage(TimeUnit.NANOSECONDS.toSeconds(s.getTotalRuntimeNanos()), runtimeInSec * DB_WRITE_THREADS)
                    );
                });
            }
        }
    }

    public static void applyArguments(CommandLineUtils.CmdArguments cmdArguments) {
        Properties prop = Utils.loadProperties(cmdArguments.getOption(dbConfigOpt).orElse(null));
        DB_WRITE_THREADS = cmdArguments.getOption(dbWriteThreadsOpt).map(Integer::valueOf).orElse(DEFAULT_DB_WRITE_THREADS);
    }

    private static final class ExecuteDbUpdate extends Thread {

        private final LazyInitializer<ExecutorService> executor;
        private final StopWatch startTime = StopWatch.createStarted();

        private ExecuteDbUpdate(LazyInitializer<ExecutorService> executor) {
            super("ExecuteDbUpdate");
            this.executor = executor;
        }

        @Override
        public void run() {
            Utils.sleep(500);
            log.info(getName() + ": STARTED");
            try {
                for (;;) {
                    boolean executed = false;
                    try {
                        AtomicInteger liveUpdatersCount = new AtomicInteger();
                        Optional<DbUpdate> dbUpdateMaxFilled;
                        synchronized (dbUpdateInstances) {
                            dbUpdateMaxFilled = dbUpdateInstances.stream()
                                    .filter(DbUpdate::isActive)
                                    .peek(d -> liveUpdatersCount.incrementAndGet())
                                    .filter(d -> !executingInstances.contains(d) && d.isExecuteNeeded())
                                    .max(Comparator.comparingInt(DbUpdate::getCacheFillPercent));
                        }
                        if (liveUpdatersCount.get() == 0 && !dbUpdateMaxFilled.isPresent() && executingInstances.isEmpty()) {
                            log.info("ExecuteDbUpdate: All updaters are closed - exiting this thread");
                            break;
                        }
                        dbUpdateMaxFilled.ifPresent(DbUpdate::executeAsync);
                        executed = dbUpdateMaxFilled.isPresent();
                        printStatsCheckPeriod();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    } finally {
                        if (!executed) {
                            Utils.sleep(10);
                        }
                    }
                }
            } finally {
                executor.get().shutdown();
                synchronized (ExecuteDbUpdate.class) {
                    log.info(getName() + ": FINISHED");
                    executeDbUpdateThread = null;
                }
            }
        }

        private static void printStatsCheckPeriod() {
            if (lastPrintedTime.getNanoTime() > PRINT_STATS_PERIOD.toNanos()) {
                lastPrintedTime.reset();
                lastPrintedTime.start();
                printStats();
            }
        }
    }

    @RequiredArgsConstructor
    @Getter
    private static class ExecStats {

        private final String tableName;
        private long executions;
        private long totalRecords;
        private long totalRuntimeNanos;

        void addExecution(long records, long runtimeNanos) {
            executions++;
            totalRecords += records;
            totalRuntimeNanos += runtimeNanos;
        }
    }
}
