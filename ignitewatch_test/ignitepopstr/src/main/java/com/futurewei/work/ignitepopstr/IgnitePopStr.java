package com.futurewei.work.ignitepopstr;

import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.ClientCacheConfiguration;
import org.apache.ignite.client.ClientTransaction;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;

public class IgnitePopStr {
    static final String CACHE_NAME = "string_cache";
    static final String VALUE = "abcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyzabcdefghigklmnopqrstuvwxyz";
    static String KEY = "key_";
    static CacheAtomicityMode cacheMode = CacheAtomicityMode.ATOMIC;

    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: [-r] [-v] [-t] [-s serverIp] -n num_queries\n" +
                    "-r deletes the cache\n" +
                    "-v prints progress every 1000 entries\n" +
                    "-t creates TRANSACTIONAL cache, default ATOMIC");
            System.exit(-1);
        }


        int exitCode = -1;
        String serverIp = "127.0.0.1";
        int entryCount = -1;
        boolean deleteCache = false;
        boolean verboseFlag = false;
        CacheAtomicityMode cacheMode = CacheAtomicityMode.ATOMIC;

        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-r"))
                deleteCache = true;
            else if (args[i].equals("-v"))
                verboseFlag = true;
            else if (args[i].equals("-n")) {
                entryCount = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-s")) {
                serverIp = args[++i];
            } else if (args[i].equals("-t")) {
                cacheMode = CacheAtomicityMode.TRANSACTIONAL;
            }
        }

        if (deleteCache) {
            try {
                ClientConfiguration clientCfg = new ClientConfiguration();
                clientCfg.setPartitionAwarenessEnabled(true);
                clientCfg.setAddresses(serverIp + ":10800");

                IgniteClient client = Ignition.startClient(clientCfg);

                client.destroyCache(CACHE_NAME);
            } catch (Exception e) {
                System.out.println("Cache " + CACHE_NAME + " does not exist!");
            }
        }

        ClientConfiguration clientCfg = new ClientConfiguration();
        clientCfg.setPartitionAwarenessEnabled(true);
        clientCfg.setAddresses(serverIp + ":10800");

        System.out.println("ARGUMENTS");
        System.out.println("entryCount = " + entryCount);

        System.out.println("serverIp = " + serverIp);
        IgniteClient client = null;

        System.out.println("ThinClient");

        long insBegin = 0, insEnd = 0;
        try {
            client = Ignition.startClient(clientCfg);
            ClientCacheConfiguration cacheConfiguration = new ClientCacheConfiguration();
            cacheConfiguration.setName(CACHE_NAME);
            cacheConfiguration.setAtomicityMode(cacheMode);
            ClientCache<String, String> stringStringClientCache = client.getOrCreateCache(cacheConfiguration);

            System.out.println("Starting inserts ...");
            ClientTransaction tx = null;

            if (cacheMode == CacheAtomicityMode.TRANSACTIONAL)
                tx = client.transactions().txStart();

            insBegin = System.nanoTime();
            for (int i = 0; i < entryCount; ++i) {
                String key = KEY + String.format("%09d", i);
                long insStamp = System.nanoTime();
                stringStringClientCache.put(key, Long.toString(insStamp) + ":" + VALUE);
            }
            stringStringClientCache.put("__LAST_ENTRY__", VALUE);
            if (cacheMode == CacheAtomicityMode.TRANSACTIONAL)
                tx.commit();
            insEnd = System.nanoTime();
            exitCode = 0;

        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            System.exit(exitCode);
        }

        System.out.println("DONE INSERTING");

        System.out.println("INSERT_TIME " + (insEnd - insBegin) / 1000 + " us");
        System.exit(exitCode);
    }
}