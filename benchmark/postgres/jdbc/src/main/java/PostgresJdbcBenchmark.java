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

import java.io.FileInputStream;
import java.io.FileReader;
import java.lang.*;
import java.sql.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
// import Adgen.*;


/**
 */
public class PostgresJdbcBenchmark {
    public static Adgen.CommandLine cmdLine = new Adgen.CommandLine();


    public static String readSchemaFile()
    {
        /*
        String tblDrp = "drop table if exists nodeinfo;";
        String tblCrt = "create table nodeinfo\n" +
                "(\n" +
                "        node_id         VARCHAR(40) PRIMARY KEY,\n" +
                "        node_name       VARCHAR(40),\n" +
                "        local_ip        VARCHAR(40),\n" +
                "        mac_address     VARCHAR(40),\n" +
                "        veth            int,\n" +
                "        host_dvr_mac    VARCHAR(18)\n" +
                ");";

        ArrayList<String> strings = new ArrayList<String>();
        strings.add(tblDrp);
        strings.add(tblCrt);
        return strings;
       */
        String tblDef = null;
        try {
            tblDef = new String(Files.readAllBytes(Paths.get(cmdLine.inputFileName)));
            return tblDef;
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(-1);
        }

        return tblDef;
    }

    public static String genInsertStmt(Adgen.TableDef tblDef)
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

    public static void bindRow(PreparedStatement insStmt, DataBuffer dataBuffer, Adgen.TableDef tblDef, int r)
    {
        ArrayList<String> row = dataBuffer.dataBuffer.get(r);
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
                        sval = row.get(i).toString();
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
            System.out.println(e);
            System.exit(-1);
        }
    }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws Exception If example execution failed.
     */
    public static void main(String[] args) throws Exception
    {
        print("JDBC example started.");

        Adgen.processArgs(args, cmdLine);

        DriverManager.getDrivers();
        // Class.forName("org.apache.ignite.IgniteJdbcThinDriver");

        // Open JDBC connection
        Connection conn = null;

        try {
            if (cmdLine.dataSource.equalsIgnoreCase("postgres")) {
                Properties prop = new Properties();
                prop.setProperty("user", cmdLine.username);
                prop.setProperty("password", cmdLine.password);
                prop.setProperty("database", cmdLine.username);
                prop.setProperty("client_encoding", "UTF8");
                prop.setProperty("allowEncodingChanges", "true");
                conn = DriverManager.getConnection(cmdLine.url, prop);
            }
            else if (cmdLine.dataSource.equalsIgnoreCase("ignite")) {
                conn = DriverManager.getConnection(cmdLine.url);
            }
            print("Connected to server.");

            // Create database objects.
            Statement stmt = conn.createStatement();
            String ddlStmt = readSchemaFile();
            stmt.executeUpdate(ddlStmt);

            print("Created database objects.");

            DataBuffer dataBuffer = new DataBuffer();
            Adgen.TableDef tblDef = Adgen.getTableDef(cmdLine.inputFileName, true);
            Adgen.genData(tblDef, dataBuffer, cmdLine.numRows);
            String insStr = genInsertStmt(tblDef);
            PreparedStatement insStmt = null;
            insStmt = conn.prepareStatement(insStr);

            long startTime = System.nanoTime();
            for (int r = 0; r < cmdLine.numRows; ++r) {
                bindRow(insStmt, dataBuffer, tblDef, r);
                insStmt.executeUpdate();
            }
            stmt.execute("commit;");
            long stopTime = System.nanoTime();
            print("Populated data: " + cmdLine.numRows + " in " + (stopTime - startTime) + " ns");

            print("Running query");
            // Get data.
            String qrySql = "select * from " + tblDef.tblName + ";";
            startTime = System.nanoTime();
            ResultSet rs = stmt.executeQuery(qrySql);
            print("Query results:");
            int resCount = 0;

            while (rs.next()) {
               ++resCount;
            }
            stopTime = System.nanoTime();
            print("Retrieved " + resCount + " rows in " + (stopTime - startTime) + " ns");
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(-1);
        }

        print("JDBC example finished.");
    }

    /**
     * Prints message.
     *
     * @param msg Message to print before all objects are printed.
     */
    private static void print(String msg) {
        System.out.println();
        System.out.println(">>> " + msg);
    }
}
