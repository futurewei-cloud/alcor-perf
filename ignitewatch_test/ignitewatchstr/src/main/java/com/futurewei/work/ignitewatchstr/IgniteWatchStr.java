package com.futurewei.work.ignitewatchstr;

import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.ClientCacheConfiguration;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.lang.IgniteBiPredicate;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class IgniteWatchStr {
    /**
     * Cache name.
     */
    private static final String CACHE_NAME = "string_cache";
    private static final String STOPWATCH_NAME = "stopIgnite.file";
    public static String TMPDIR;
    public static String stopWatch;
    private static CountDownLatch countDownLatch;
    private final String serverIp;
    private final int queryCount;
    private final int numWorkers;
    private final int[] ipAddress = new int[4];
    private final int[] macAddress = new int[6];
    private final ArrayList<Long> queryTimes;
    private final ArrayList<Long> fetchTimes;
    private final ContinuousQuery<String, String> watchQuery;
    private final boolean verboseFlag;
    private final boolean useInitialQuery;
    private CacheAtomicityMode cacheMode;
    private long firstPut;
    private long lastPut;
    private long firstGet;
    private long lastGet;
    private ClientConfiguration clientCfg;
    private int queryIndex;
    private IgniteClient client;
    private ClientCache<String, String> stringClientCache;
    private String firstNodeId = null;
    private String lastNodeId = null;
    private boolean stopSignalSeen;

    public IgniteWatchStr(String serverIp, CountDownLatch countDownLatch, int queryCount, int numWorkers, boolean verboseFlag, CacheAtomicityMode cacheMode, boolean useInitialQuery) {
        this.serverIp = serverIp;
        this.queryCount = queryCount;
        this.numWorkers = numWorkers;
        this.verboseFlag = verboseFlag;
        this.useInitialQuery = useInitialQuery;
        this.cacheMode = cacheMode;
        ipAddress[0] = ipAddress[1] = ipAddress[2] = ipAddress[3] = 1;
        macAddress[0] = macAddress[1] = macAddress[2] = macAddress[3] = macAddress[4] = macAddress[5] = 1;

        queryTimes = new ArrayList<>();
        fetchTimes = new ArrayList<>();
        watchQuery = new ContinuousQuery<>();

        IgniteWatchStr.countDownLatch = countDownLatch;
        queryIndex = 0;
        firstPut = 0;
        lastPut = 0;
        firstGet = 0;
        lastGet = 0;
        stopSignalSeen = false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: [-r] [-v] [-t] [-i] -q num_queries [-w num_workers] [-s serverIp]\n" +
                    "-r deletes the cache\n" +
                    "-v prints progress every 1000 entries\n" +
                    "-t creates TRANSACTIONAL cache, default ATOMIC\n" +
                    "-i use initial query to pull entries existing before WATCHER starts.\n" +
                    "***** If -i is specified, start watchers after inserts (not working yet) *****\n" +
                    "to stop watchers and print metrics create a file stopIgnite.file under $TMPDIR or\n" +
                    "$TMP, or $TEMP");
            System.exit(-1);
        }

        String serverIp = "127.0.0.1";
        int queryCount = 1;
        int numWorkers = 1;
        boolean deleteCache = false;
        boolean verboseFlag = false;
        boolean useInitialQuery = false;
        CacheAtomicityMode cacheMode = CacheAtomicityMode.ATOMIC;

        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-r"))
                deleteCache = true;
            else if (args[i].equals("-v"))
                verboseFlag = true;
            else if (args[i].equals("-q")) {
                queryCount = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-w")) {
                numWorkers = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-s")) {
                serverIp = args[++i];
            } else if (args[i].equals("-t")) {
                cacheMode = CacheAtomicityMode.TRANSACTIONAL;
            } else if (args[i].equals("-i")) {
                useInitialQuery = true;
            }
        }

        if (deleteCache) {
            try {
                ClientConfiguration clientCfg = new ClientConfiguration();
                clientCfg.setPartitionAwarenessEnabled(true);
                clientCfg.setAddresses(serverIp + ":10800");

                IgniteClient client = Ignition.startClient(clientCfg);

                client.destroyCache(CACHE_NAME);
                client = null;
            } catch (Exception e) {
                System.out.println("Cache " + CACHE_NAME + " does not exist!");
            }
        }

        TMPDIR = System.getenv("TMPDIR");
        if (TMPDIR == null)
            TMPDIR = System.getenv("TEMP");
        if (TMPDIR == null)
            TMPDIR = System.getenv("TMP");
        if (TMPDIR == null)
            TMPDIR = ".";
        stopWatch = TMPDIR + File.separator + STOPWATCH_NAME;
        File file = new File(stopWatch);
        file.delete();
        System.out.println("ARGUMENTS");
        System.out.println("serverIP     = " + serverIp);
        System.out.println("queryCount   = " + queryCount);
        System.out.println("numWorkers   = " + numWorkers);
        System.out.println("deleteCache   = " + deleteCache);
        System.out.println("verboseFlag   = " + verboseFlag);
        System.out.println("Cache Name   = " + CACHE_NAME);
        System.out.println("IGNITE_HOME = " + System.getenv("IGNITE_HOME"));
        System.out.println("stopWatch   = " + stopWatch);
        System.out.println("TXN         = " + cacheMode);

        CountDownLatch countDownLatch = new CountDownLatch(numWorkers);
        for (int i = 0; i < numWorkers; ++i) {
            System.out.println("Starting worker " + i);
            final int wid = i;
            final String sip = serverIp;
            final int qryCount = queryCount;
            final int numThreads = numWorkers;
            final boolean beVerbose = verboseFlag;
            final CacheAtomicityMode myCacheMode = cacheMode;
            final boolean myUseInitialQuery = useInitialQuery;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Run entered tid " + Thread.currentThread().getId());
                    IgniteWatchStr igniteWatchStr = new IgniteWatchStr(sip, countDownLatch, qryCount, numThreads, beVerbose, myCacheMode, myUseInitialQuery);
                    System.out.println("Setting up tid " + Thread.currentThread().getId() + " ...");
                    igniteWatchStr.setup();
                    System.out.println("Starting CQ tid " + Thread.currentThread().getId() + " ...");
                    igniteWatchStr.workerFunc(wid);
                    System.out.println("Finished tid " + Thread.currentThread().getId());
                }
            });

            t.start();
            System.out.println("Exiting tid " + Thread.currentThread().getId());
        }

        try {
            while (countDownLatch.getCount() > 0)
                ;
        } catch (Exception e) {
            System.out.println("CountDownLatch Exception " + e.getMessage());
        }

        System.exit(0);
    }

    public void setup() {
        clientCfg = new ClientConfiguration();
        clientCfg.setPartitionAwarenessEnabled(true);
        clientCfg.setAddresses(this.serverIp + ":10800");

        client = Ignition.startClient(clientCfg);

        ClientCacheConfiguration cacheConfig = new ClientCacheConfiguration();
        cacheConfig.setName(CACHE_NAME);
        cacheConfig.setAtomicityMode(cacheMode);

        stringClientCache = client.getOrCreateCache(cacheConfig);
        firstNodeId = String.format("key_%09d", 0);
        lastNodeId = String.format("key_%09d", queryCount - 1);
    }

    public void printQueryTimes() {
        System.out.println("queryCount " + queryCount + " queryIndex " + queryIndex);
        System.out.println("QUERY_TIMES_BEGIN");
        for (int i = 0; i < queryTimes.size(); ++i)
            System.out.println("TID " + Thread.currentThread().getId() + " " + "WATCH_TIME " + queryTimes.get(i));
        System.out.println("QUERY_TIMES_END");
        System.out.println("FIRST " + firstNodeId + " LAST " + lastNodeId);
        System.out.println("FIRST_GET " + (firstGet / 1000) + " FIRST_PUT " + firstPut + " LAST_GET " + (lastGet / 1000) + " LAST_PUT " + (lastPut / 1000));
        System.out.println("FIRST_LAST_LATENCY_GET " + (lastGet - firstGet) / 1000);
    }

    public void workerFunc(int wid) {
        long tid = Thread.currentThread().getId();
        System.out.println("Worker " + wid + ", tid = " + tid + " is running ...");
        FileReader stopper;
        if (useInitialQuery) {
            watchQuery.setInitialQuery(new ScanQuery<>(new IgniteBiPredicate<String, String>() {
                @Override
                public boolean apply(String key, String val) {
                    return true;
                }
            }));
        }
        // Callback that is called locally when update notifications are received.
        watchQuery.setLocalListener(evts -> {
            for (CacheEntryEvent<? extends String, ? extends String> e : evts) {
                Long myTime = System.nanoTime();
                if (e.getKey().equals("__LAST_ENTRY__")) {
                    stopSignalSeen = true;
                    System.out.println("Thread " + Thread.currentThread().getId() + " watch break on stop signal");
                    break;
                }
                String value = e.getValue();
                String insStampStr = value.substring(0, value.indexOf(":"));
                long entryTime = Long.valueOf(insStampStr);
                if (firstPut == 0 && e.getKey().equals(firstNodeId))
                    firstPut = entryTime;
                if (firstGet == 0)
                    firstGet = myTime;
                lastGet = myTime;
                lastPut = entryTime;
                queryTimes.add((myTime - entryTime) / 1000);
                ++queryIndex;
                if (verboseFlag) {
                    if ((queryIndex % 1000) == 0)
                        System.out.println("tid = " + tid + " queryIndex = " + queryIndex);
                }
            }
        });

        System.out.println("Thread " + Thread.currentThread().getId() + " seen stop signal");
        try (QueryCursor<Cache.Entry<String, String>> cursor = stringClientCache.query(watchQuery)) {
            // Iterating over the entries returned by the initial query
            int i = 0;
            for (Cache.Entry<String, String> e : cursor) {
                if (i == 0)
                    firstGet = System.nanoTime();
                lastGet = System.nanoTime();
                ++i;
            }

            System.out.println("INITQUERY LAST_GET " + (lastGet / 1000) + " FIRST_GET " + (firstGet / 1000) +
                    " LATENCY " + ((lastGet - firstGet) / 1000));
        }
        catch (Exception ie) {
            System.out.println("Initial Query Exception : " + ie.getMessage());
        }

        System.out.println("tid = " + tid + ", Submitting query ... " + System.nanoTime());
        stringClientCache.query(watchQuery);
        System.out.println("tid = " + tid + ", Query submitted ... " + System.nanoTime());

        while (queryIndex < queryCount) {
            try {
                if (verboseFlag)
                    System.out.println("tid = " + tid + ", Waiting ... QC " + queryCount + " QI " + queryIndex + " CT " + System.nanoTime());
                Thread.sleep(1000);
                if (stopSignalSeen) {
                    System.out.println("tid = " + tid + ", Stopping on stop signal");
                    break;
                }
                stopper = new FileReader(stopWatch);
                stopper.close();
                System.out.println("tid = " + tid + ", Stopping on stop file");
                break;

            } catch (Exception e) {
                if (verboseFlag)
                    System.out.println("tid = " + tid + ", Exception: " + e.getMessage());
            }
        }

        printQueryTimes();
        System.out.flush();
        System.out.println("Worker " + wid + ", tid = " + tid + ", is stopping, CDL = " + countDownLatch.toString());
        countDownLatch.countDown();
        System.out.println("tid = " + tid + ", CDL " + countDownLatch.toString());
        Thread.currentThread().stop();
    }
}