/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

/**
 */
public class AlcorJdbcBenchmark {
    public Adgen.CommandLine cmdLine = new Adgen.CommandLine();

    public static class OperationTimes {
        public String opType = null;
        public int opCount = 0;
        public long opMin = Long.MAX_VALUE;
        public long opMax = Long.MIN_VALUE;
        public int numRows = 0;
        public double opSum = 0.0;
        public long[] latency = null;
        public long opStart = 0;

        public OperationTimes(String opDesc, long firstRow, long lastRow)
        {
            opType = opDesc;
            numRows = (int)(lastRow - firstRow + 1);
            latency = new long[numRows]; // +1 for the single query
        }

        public void startOp()
        {
            latency[opCount] = System.nanoTime();
        }

        public void finishOp()
        {
            long opTime = System.nanoTime() - latency[opCount];
            latency[opCount++] = opTime;
            // System.out.println("opType = " + opType + " opTime = " + opTime);
        }

        public void printTimes() throws Exception
        {
            for (int i = 0; i < opCount; ++i)
                System.out.println(opType + " " + Thread.currentThread().getId() + " " + latency[i]);
        }
    }

    public AlcorJdbcBenchmark.OperationTimes qryTimes = null;

    public class WorkerThread extends Thread {
        String workerStmt;
        Adgen.CommandLine workerCmd;
        Adgen.TableDef workerTbl;
        DataBuffer workerData;
        CountDownLatch workerLatch;
        Properties workerProp;
        OperationTimes opTimes;
        long workerStartRow;
        long workerLastRow;

        public WorkerThread(String insString, Adgen.CommandLine cmdLine, Adgen.TableDef tblDef, DataBuffer dataBuffer,
                            long firstRow, long lastRow, CountDownLatch activeLatch, Properties prop, String opDesc)
        {
            workerStmt = insString;
            workerCmd  = cmdLine;
            workerTbl  = tblDef;
            workerData  = dataBuffer;
            workerStartRow = firstRow;
            workerLastRow   = lastRow;
            workerLatch = activeLatch;
            workerProp  = prop;
            opTimes = new OperationTimes(opDesc, firstRow, lastRow);
        }

        @Override
        public void run()
        {
            try {
                Connection conn;
                if (workerProp != null)
                    conn = DriverManager.getConnection(workerCmd.url, workerProp);
                else
                    conn = DriverManager.getConnection(workerCmd.url);

                PreparedStatement prepStmt = conn.prepareStatement(workerStmt);
                int rc = 0;

                if (cmdLine.verboseMode) {
                    printWallClock("[" + Thread.currentThread().getId() + "] Staring chunk INSERT(" +
                        workerStartRow + ", " + workerLastRow + ") at " + System.nanoTime());
                }
                long startTime = System.nanoTime();
                for (long i = workerStartRow; i <= workerLastRow && i < cmdLine.numRows; ++rc, ++i) {
                    bindRow(prepStmt, workerData, workerTbl, i);
                    try {
                        opTimes.startOp();
                        prepStmt.executeUpdate();
                        opTimes.finishOp();
                    } catch (Exception e) {
                        // continue;
                    }
                }
                long stopTime = System.nanoTime();
                opTimes.printTimes();
                if (cmdLine.verboseMode) {
                    printWallClock("[" + Thread.currentThread().getId() + "] Finished chunk INSERT at " + System.nanoTime());
                    print("[" + Thread.currentThread().getId() + "] Inserted " + rc + " rows in " + (stopTime - startTime));
                }
            }
            catch (Exception e) {
                print(e.toString());
            }

            workerLatch.countDown();
        }
    }

    public void  doInsert(String insStr, Adgen.CommandLine cmdLine, Adgen.TableDef tblDef, DataBuffer dataBuffer, Properties prop)
    {
        long workerRows = cmdLine.numRows / cmdLine.numWorkers;
        long firstRow = 0;
        long lastRow = workerRows - 1;
        WorkerThread[] workers = new WorkerThread[cmdLine.numWorkers];

        CountDownLatch activeLatch = new CountDownLatch(cmdLine.numWorkers);
        try {
            for (int i = 0; i < cmdLine.numWorkers; ++i) {
                if (cmdLine.verboseMode) {
                    print("Calling " + i + "'th thread with " + firstRow + ", " + lastRow);
                }
                workers[i] = new WorkerThread(insStr, cmdLine, tblDef, dataBuffer, firstRow, lastRow, activeLatch, prop, "Insert");
                workers[i].start();
                firstRow = lastRow + 1;
                lastRow  += workerRows;
            }

            activeLatch.await();
        }
        catch (Exception e) {
            print(e.toString());
        }
    }

    public String readSchemaFile(Adgen.CommandLine cmdLine)
    {
        String tblDef = null;
        try {
            tblDef = new String(Files.readAllBytes(Paths.get(cmdLine.inputFileName)));
            return tblDef;
        }
        catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);
        }

        return tblDef;
    }

    public String genInsertStmt(Adgen.TableDef tblDef)
    {
        StringBuilder stmt = new StringBuilder("INSERT INTO ");
        stmt.append(tblDef.tblName);
        stmt.append(" (");
        for (int i = 0; i < tblDef.columns.size(); ++i) {
            stmt.append(tblDef.columns.get(i).name);
            if (i < tblDef.columns.size() - 1)
                stmt.append(", ");
        }

        stmt.append(") VALUES (");
        for (int i = 0; i < tblDef.columns.size(); ++i) {
            stmt.append("?");
            if (i < tblDef.columns.size() - 1)
                stmt.append(", ");
        }
        stmt.append(");");

        return stmt.toString();
    }

    public void bindRow(PreparedStatement insStmt, DataBuffer dataBuffer, Adgen.TableDef tblDef, long r)
    {
        ArrayList<String> row = dataBuffer.dataBuffer.get((int) r);
        try {
            for (int i = 0; i < row.size(); ++i) {
                int p = i + 1;
                String sval;
                int ival;
                switch (tblDef.columns.get(i).type) {
                    case AT_IP:
                    case AT_IPV4:
                    case AT_IPV6:
                    case AT_MAC:
                    case AT_STRING:
                        sval = row.get(i);
                        insStmt.setString(p, sval);
                        break;
                    case AT_INT:
                        ival = Integer.valueOf(row.get(i));
                        insStmt.setInt(p, ival);
                        break;
                    default:
                        System.out.println("Unexpected type");
                        System.exit(-1);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);
        }
    }

    public void printWallClock(String header)
    {
        LocalDateTime dateTime = LocalDateTime.now(); // Gets the current date and time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        System.out.println((header != null ? header : "") + " " + dateTime.format(formatter));
    }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     */
    public static void main(String[] args)
    {
        long insTime = 0, qryTime = 0;
        AlcorJdbcBenchmark benchmark = new AlcorJdbcBenchmark();
        Adgen.processArgs(args, benchmark.cmdLine);

        if (benchmark.cmdLine.verboseMode) {
            benchmark.print("Alcor Benchmark started");
        }

        int numCpu = Runtime.getRuntime().availableProcessors();
        // adjust number of rows to be a multiple of number of threads
        // select (100000 / 20) + (100000 % 20 * 20);
        long workerRows = (long)(benchmark.cmdLine.numRows / benchmark.cmdLine.numWorkers) + (long)((benchmark.cmdLine.numRows % benchmark.cmdLine.numWorkers) * benchmark.cmdLine.numWorkers);
        long numRows = workerRows * benchmark.cmdLine.numWorkers;
        if (numRows != benchmark.cmdLine.numRows) {
            System.out.println("Number of rows adjusted to " + numRows + " multiple of " + benchmark.cmdLine.numWorkers);
            benchmark.cmdLine.numRows = numRows;
        }

        DriverManager.getDrivers();

        // Open JDBC connection
        Connection conn = null;
        Properties prop = null;

        try {
            if (benchmark.cmdLine.dataSource.equalsIgnoreCase("postgres")) {
                prop = new Properties();
                if (benchmark.cmdLine.username != null)
                    prop.setProperty("user", benchmark.cmdLine.username);
                if (benchmark.cmdLine.password != null)
                    prop.setProperty("password", benchmark.cmdLine.password);
                if (benchmark.cmdLine.dataSource != null)
                    prop.setProperty("database", benchmark.cmdLine.dataSource);
                prop.setProperty("client_encoding", "UTF8");
                prop.setProperty("allowEncodingChanges", "true");
                conn = DriverManager.getConnection(benchmark.cmdLine.url, prop);
            }
            else if (benchmark.cmdLine.dataSource.equalsIgnoreCase("ignite")) {
                conn = DriverManager.getConnection(benchmark.cmdLine.url);
            }
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Connected to server.");
            }

            // Create database objects.
            Statement stmt = conn.createStatement();
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Created DDL Statements");
            }
            String ddlStmt = benchmark.readSchemaFile(benchmark.cmdLine);
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Read schema file");
            }
            if (!benchmark.cmdLine.noDDl) {
                stmt.executeUpdate(ddlStmt);
                if (benchmark.cmdLine.verboseMode) {
                    benchmark.print("Created database objects.");
                }
            }

            DataBuffer dataBuffer = new DataBuffer();
            Adgen.TableDef tblDef = Adgen.getTableDef(benchmark.cmdLine.inputFileName, true);
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Created Table Definition");
            }

            if (benchmark.cmdLine.noDDl) {
                String delStmt = "DELETE FROM " + tblDef.tblName + ";";
                stmt.executeUpdate(delStmt);
                Thread.sleep(1000);
            }
            if (benchmark.cmdLine.verboseMode) {
                benchmark.printWallClock("Starting GENDATA");
            }
            long startTime = System.nanoTime();
            Adgen.genData(tblDef, dataBuffer, benchmark.cmdLine.numRows);
            long stopTime = System.nanoTime();
            if (benchmark.cmdLine.verboseMode) {
                benchmark.printWallClock("Finished GENDATA");
                benchmark.print("Generated data: " + benchmark.cmdLine.numRows + " in " + (stopTime - startTime) + " ns");
            }
            String insStr = benchmark.genInsertStmt(tblDef);
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Created INSERT statement");
                benchmark.printWallClock("Staring Table INSERT at");
            }
            startTime = System.nanoTime();
            benchmark.doInsert(insStr, benchmark.cmdLine, tblDef, dataBuffer, prop);
            stopTime = System.nanoTime();
            if (benchmark.cmdLine.verboseMode) {
                benchmark.printWallClock("INSERT Table finished");
            }
            insTime = stopTime - startTime;
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Populated data: " + benchmark.cmdLine.numRows + " in " + insTime + " ns");
                benchmark.print("Running query");
            }
            // Get data.
            String qrySql = "select * from " + tblDef.tblName + ";";
            if (benchmark.cmdLine.verboseMode) {
                benchmark.printWallClock("Starting QUERY");
            }
            startTime = System.nanoTime();
            ResultSet rs = stmt.executeQuery(qrySql);
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Query results:");
            }
            int resCount = 0;

            benchmark.qryTimes = new AlcorJdbcBenchmark.OperationTimes("Query", (long)0, (long)(benchmark.cmdLine.numRows - 1));

            for (resCount = 0; resCount < benchmark.cmdLine.numRows; ++resCount) {
                benchmark.qryTimes.startOp();
                if (!rs.next())
                    break;
                benchmark.qryTimes.finishOp();
            }

            benchmark.qryTimes.printTimes();
            stopTime = System.nanoTime();
            if (benchmark.cmdLine.verboseMode) {
                benchmark.printWallClock("Finished QUERY");
            }
            qryTime = stopTime - startTime;
            if (benchmark.cmdLine.verboseMode) {
                benchmark.print("Retrieved " + resCount + " rows in " + qryTime + " ns");
            }
        }
        catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);
        }

        benchmark.print("#METRIC#Run " + benchmark.cmdLine.dataSource + " " + benchmark.cmdLine.runTitle + " " + benchmark.cmdLine.numWorkers);
        benchmark.print("#METRIC" + benchmark.cmdLine.numRows + " " + (double)insTime + " " + (double)qryTime);
        if (benchmark.cmdLine.verboseMode) {
            benchmark.print("Alcor Benchmark finished.");
        }
    }

    /**
     * Prints message.
     *
     * @param msg Message to print before all objects are printed.
     */
    private void print(String msg)
    {
        System.out.println(msg);
    }
}
