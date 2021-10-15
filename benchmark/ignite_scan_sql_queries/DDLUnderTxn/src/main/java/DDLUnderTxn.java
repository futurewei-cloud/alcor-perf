import org.apache.ignite.*;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.query.*;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.ClientCacheConfiguration;
import org.apache.ignite.client.ClientTransaction;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.lang.IgniteBiPredicate;

import javax.cache.Cache;
import java.util.*;

// Measure SQLFieldQuery and Scan query performance
// This is all cluttered.

public class DDLUnderTxn {

    // SQLFieldQuery version of the Classes
    static class Address {
        public int houseNumber;
        public String streetName;

        @QuerySqlField(index = true)
        public String city;
        public String state;
        public String zip;

        public Address(int hn, String sn, String c, String s, String z) {
            houseNumber = hn;
            streetName = new String(sn);
            city = new String(c);
            state = new String(s);
            zip = new String(z);
        }

        public Address(Address other) {
            houseNumber = other.houseNumber;
            streetName = new String(other.streetName);
            city = new String(other.city);
            state = new String(other.state);
            zip = new String(other.zip);
        }

        public String getCity() {
            return city;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(houseNumber).append(", ").append(streetName).
                    append("\n").append(city).append(", ").
                    append(state).append(" ").append(zip);

            return sb.toString();
        }
    }

    static class PersonSQL {
        public PersonSQL(String ssn, String name, Address adrs) {
            this.ssn = new String(ssn);
            this.name = new String(name);
            this.address = new Address(adrs);
        }

        public void setName(String name) {
            this.name = new String(name);
        }

        public void setSsn(String ssn) {
            this.ssn = new String(ssn);
        }


        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer(ssn).append(" ").append(name);
            if (address != null)
                sb.append("\n").append(address.toString()).toString();

            return sb.toString();
        }

        public String getSsn() {
            return ssn;
        }

        public String getName() {
            return name;
        }

        public Address getAddress() {
            return address;
        }

        @QuerySqlField(index = true)
        private String ssn;

        @QuerySqlField(index = true)
        private String name;

        @QuerySqlField
        private Address address;
    }

    // ScanQuery versions
    static class AddressSCN {
        public int houseNumber;
        public String streetName;

        public String city;
        public String state;
        public String zip;

        public AddressSCN(int hn, String sn, String c, String s, String z) {
            houseNumber = hn;
            streetName = new String(sn);
            city = new String(c);
            state = new String(s);
            zip = new String(z);
        }

        public AddressSCN(AddressSCN other) {
            houseNumber = other.houseNumber;
            streetName = new String(other.streetName);
            city = new String(other.city);
            state = new String(other.state);
            zip = new String(other.zip);
        }

        public AddressSCN(Address other) {
            houseNumber = other.houseNumber;
            streetName = new String(other.streetName);
            city = new String(other.city);
            state = new String(other.state);
            zip = new String(other.zip);
        }

        public String getCity() {
            return city;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(houseNumber).append(", ").append(streetName).
                    append("\n").append(city).append(", ").
                    append(state).append(" ").append(zip);

            return sb.toString();
        }
    }

    static class PersonSCN {
        public PersonSCN(String ssn, String name, AddressSCN adrs) {
            this.ssn = new String(ssn);
            this.name = new String(name);
            this.address = new AddressSCN(adrs);
        }

        public PersonSCN(PersonSQL other) {
            ssn = new String(other.getSsn());
            name = new String(other.getName());
            address = new AddressSCN(other.getAddress());
        }

        public void setName(String name) {
            this.name = new String(name);
        }

        public void setSsn(String ssn) {
            this.ssn = new String(ssn);
        }


        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer(ssn).append(" ").append(name);
            if (address != null)
                sb.append("\n").append(address.toString()).toString();

            return sb.toString();
        }

        public String getSsn() {
            return ssn;
        }

        public String getName() {
            return name;
        }

        public AddressSCN getAddress() {
            return address;
        }

        public String getCity() {
            return address.getCity();
        }

        private String ssn;

        private String name;

        private AddressSCN address;
    }


    public DDLUnderTxn(String uuid) {
    }


    public static void main(String[] args) {
        final String tblName = "PersonSQL";
        final String schName = tblName;
        final String scanCacheName = "PersonSCN";
        final String cacheName = "\"" + schName + "\"." + tblName;

        int exitCode = -1;

        int numEntries = 1000000;
        int sample = 1000;
        int actualSample = 0;

        if (args.length >= 1) {
            numEntries = Integer.valueOf(args[0]);
            sample = numEntries / 1000;
            if (sample <= 1)
                sample = 5; // actual sample may be even less
        }

        System.out.println("Adding " + numEntries + " entries, sampling " + sample + " entires");
        ClientConfiguration clientCfg = new ClientConfiguration();
        clientCfg.setPartitionAwarenessEnabled(true);
        clientCfg.setAddresses("127.0.0.1:10800");

        IgniteClient client = null;

        Random rnd = new Random();

        try {
            client = Ignition.startClient(clientCfg);
        } catch (Exception e) {
            System.out.println("Cannot connect to Local server");
            System.exit(exitCode);
        }

        try {
            client.destroyCache(tblName);
            client.destroyCache(scanCacheName);
        } catch (Exception e) {
            System.out.println("Cache doesn't exist: " + e.getMessage());
        }

        try {

            // SQLFiledQuery version of the cache
            ClientCacheConfiguration personCacheConfiguration = new ClientCacheConfiguration();
            personCacheConfiguration.setName(tblName);
            personCacheConfiguration.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

            QueryEntity qryEnt = new QueryEntity();
            qryEnt.setKeyType(String.class.getName());
            qryEnt.setValueType(PersonSQL.class.getName());
            qryEnt.addQueryField("name", String.class.getName(), null);

            qryEnt.setKeyType(String.class.getName());
            qryEnt.setValueType(PersonSQL.class.getName());
            qryEnt.addQueryField("ssn", String.class.getName(), null);

            qryEnt.addQueryField("address", Address.class.getName(), null);

            qryEnt.addQueryField("address.city", Address.class.getField("city").getName(), null);

            QueryIndex nameIdx = new QueryIndex("name");
            QueryIndex cityIndex = new QueryIndex("address.city");
            // cityIndex.setIndexType(QueryIndexType.FULLTEXT);
            qryEnt.setIndexes(Collections.singletonList(nameIdx));
            personCacheConfiguration.setQueryEntities(qryEnt);

            ClientCache<String, PersonSQL> personIgniteCache = client.getOrCreateCache(personCacheConfiguration);

            // ScanQuery version
            ClientCacheConfiguration scanCfg = new ClientCacheConfiguration();
            scanCfg.setName(scanCacheName);
            scanCfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
            ClientCache<String, PersonSCN> personSCNClientCache = client.getOrCreateCache(scanCfg);

            // hold on to sample number of inserted entries to run queries
            PersonSQL[] samplePers = new PersonSQL[numEntries];
            long[] qryTimes = new long[sample];
            long[] resCount = new long[sample];
            int j = 0;
            ClientTransaction ttxn = null;
            Boolean noTxn = true;
            int i;
            long popStart = System.nanoTime();
            for (i = 0; i < numEntries; ++i) {
                try {
                    // TXN are done inside manually (no try ...) to avoid OOM when
                    // inserting millions of rows.
                    if (noTxn) {
                        ttxn = client.transactions().txStart();
                        noTxn = false;
                    }
                    PersonSQL person = createPerson(rnd);
                    personIgniteCache.put(person.getSsn(), person);

                    // put it into Scan version too.
                    PersonSCN personSCN = new PersonSCN(person);
                    personSCNClientCache.put(personSCN.getSsn(), personSCN);

                    if ((i % sample) == 0) {
                        samplePers[j++] = person;
                        actualSample++;
                        ttxn.commit();
                        noTxn = true;
                    }
                } catch (Exception e) {
                    System.out.println("Failed to add entries to PersonCache" + e.getMessage());
                    ttxn.rollback();
                    throw e;
                }
            }
            long popFinish = System.nanoTime();
            if (noTxn == false)
                ttxn.commit();
            long elapsedTime = (popFinish - popStart) / 1000;
            System.out.println("Added " + i + " Entries in PersonSQL, PersonSCN, time = " + elapsedTime + " us");
            int qryCount = Math.min(actualSample, sample);

            runSQLQuery(client, personIgniteCache, tblName, schName, samplePers, qryCount);

            System.out.println();
            System.out.println("Try Scan query");
            runScanQueryBM_Alcor(client, personSCNClientCache, rnd, samplePers, sample, numEntries);
            runScanQueryBM_Ign01(client, personSCNClientCache, rnd, samplePers, sample, numEntries);
            runScanQueryBM_Iter(client, personSCNClientCache, rnd, samplePers, sample, numEntries);
        } catch (Exception e) {
            System.out.println("Failed to instantiate PersonCache : " + e.getMessage());
            // throw e;
        } finally {
            try {
                if (client != null)
                    client.close();
            } catch (Exception e) {
            }
            System.exit(exitCode);
        }
    }

    static PersonSQL createPerson(Random rnd) {
        int num = rnd.nextInt();
        Integer n1 = Math.abs(num % 10000);
        num /= 10000;
        Integer n2 = Math.abs(num % 10000);
        num /= 10000;
        Integer n3 = Math.abs(num % 1000);

        StringBuilder ssn = new StringBuilder(n1.toString());
        ssn.append("-").append(n3.toString()).append("-").append(n2.toString());
        String fname = randomName(8, rnd);

        int hno = rnd.nextInt(10000);
        String street = randomName(12, rnd);
        String city = randomName(10, rnd);
        String state = randomName(9, rnd);
        Integer nzip;

        while (true) {
            nzip = rnd.nextInt(100000);
            if (nzip >= 10001)
                break;
            nzip = rnd.nextInt();
        }

        String zip = nzip.toString();

        Address addr = new Address(hno, street, city, state, zip);
        PersonSQL per = new PersonSQL(ssn.toString(), fname, addr);

        return per;
    }

    public static String randomName(int len, Random rnd) {

        final char[] letters = {'a', 'q', 'z', 'w', 's', 'x', 'p', 'l', 'm', 'e'
                , 'd', 'c', 'o', 'k', 'n', 'r', 'f', 'v', 'b', 'g', 't', 'i', 'j', 'u', 'y', 'h'};

        int num = Math.abs(rnd.nextInt());
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < len; ++i) {
            if (num == 0)
                num = Math.abs(rnd.nextInt());
            int d = num % 26;
            sb.append(letters[d]);
            num /= 26;
        }

        return sb.toString();
    }

    public static void runSQLQuery(IgniteClient client, ClientCache<String, PersonSQL> personIgniteCache, String tblName, String schName, PersonSQL[] samplePers, int qryCount) {
        System.out.println();
        System.out.println("Testing QueryEntity, Indexing, SQL Access to ClientCache from ThinClient");

        long[] qryTimes = new long[qryCount];
        System.out.println("Running " + qryCount + " lookup queries");
        boolean qrySuccess = false;
        int i = 0;
        for (i = 0; i < qryCount; ++i) {
            StringBuilder qry = new StringBuilder("select ssn, name, address from");
            qry.append("\"").append(schName).append("\".");
            qry.append(tblName).append(" where name = ").append("'");
            qry.append(samplePers[i].getName()).append("'");

            SqlFieldsQuery sql = new SqlFieldsQuery(qry.toString());

            try {
                long t1 = System.nanoTime();
                QueryCursor<List<?>> cursor = personIgniteCache.query(sql);
                long t2 = System.nanoTime();
                qryTimes[i] = (t2 - t1) / 1000;
                for (List<?> row : cursor) {
                    String ssn = row.get(0).toString();
                    String name = row.get(1).toString();
                    Address addrs = (Address) row.get(2);
                    PersonSQL p = new PersonSQL(ssn, name, addrs);
                    System.out.println(p.toString());
                    System.out.println();
                }
                qrySuccess = true;
            } catch (Exception e) {
                System.out.println("SQL Query failed: " + e.getMessage());
            }
        }

        if (qrySuccess) {
            System.out.println("SQLFieldQuery times (us)");
            for (int k = 0; k < i; ++k)
                System.out.println(qryTimes[k]);
        }
    }

    // ScanQuery the way Alcor does it
    public static void runScanQueryBM_Alcor(IgniteClient client, ClientCache<String, PersonSCN> cache, Random rnd, PersonSQL[] samplePers, int qryCount, int numEntries) {
        System.out.println("Scan Query Alcor version");
        long[] qryTimes = new long[qryCount];
        boolean qrySuccess = false;
        int i = 0;
        for (i = 0; i < qryCount; ++i) {
            Map<String, Object[]> queryParams = new HashMap<>();
            Object[] values = new Object[1];
            values[0] = samplePers[i].getName();
            queryParams.put("name", values);
            IgniteBiPredicate<String, BinaryObject> pred = MapPredicate.getInstance(queryParams);
            QueryCursor<Cache.Entry<String, BinaryObject>> cursor = cache.withKeepBinary().query(
                    ScanQueryBuilder.newScanQuery(pred));
            try {
                List<Cache.Entry<String, BinaryObject>> result = cursor.getAll();
                if (result.isEmpty())
                    continue;
                BinaryObject obj = result.get(0).getValue();
                if (obj instanceof BinaryObject) {
                    BinaryObject binObj = (BinaryObject) obj;
                    PersonSCN p = (PersonSCN) binObj.deserialize();
                    System.out.println(p.toString());
                }
                qrySuccess = true;
            }
            catch (Exception e) {
                System.out.println("Scan Query (Alcor) failed " + e.getMessage());
                break;
            }
        }

        if (qrySuccess) {
            System.out.println("ScanQuery times (us)");
            for (int k = 0; k < i; ++k)
                System.out.println(qryTimes[k]);
        }
    }

    public static void runScanQueryBM_Ign01(IgniteClient client, ClientCache<String, PersonSCN> cache, Random rnd, PersonSQL[] samplePers, int qryCount, int numEntries) {
        System.out.println("Scan Query Ignite Version 01");
        long[] qryTimes = new long[qryCount];
        int resCount = 0;
        int i = 0;
        for (i = 0; i < qryCount; ++i) {
            String srchStr = samplePers[i].getName();
            IgniteBiPredicate<String, PersonSCN> filter = (key, p) -> p.getName().equals(srchStr);

            // works without a filter: How to time these? worry about it after it works.
            try (QueryCursor<Cache.Entry<String, PersonSCN>> qryCursor = cache.query(new ScanQuery<>(filter))) {
                qryCursor.forEach(
                        entry -> System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue()));
                ++resCount;
            } catch (Exception e) {
                System.out.println("Scan query (Ignite 01) failed " + e.getMessage());
                break;
            }
        }
    }

    public static void runScanQueryBM_Iter(IgniteClient client, ClientCache<String, PersonSCN> cache, Random rnd, PersonSQL[] samplePers, int qryCount, int numEntries) {
        System.out.println("Scan Query Ignite Version 01");
        int i = 0;
        for (i = 0; i < qryCount; ++i) {
            String srchStr = samplePers[i].getName();
            try (QueryCursor cursor = cache.query(new ScanQuery<String, PersonSCN>((k, p) -> p.getName().equals(srchStr)))) {
                for (Object o : cursor)
                    System.out.println(o.toString());
            }
            catch (Exception e) {
                System.out.println("Iter failed " + e.getMessage());
            }
        }
    }
}