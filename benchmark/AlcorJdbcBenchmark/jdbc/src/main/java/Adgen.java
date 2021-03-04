import java.lang.*;
import java.util.*;
import java.math.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Adgen
{
    // How many times to try for a unique value before bailing out
    public final static int MaxHashIter = 100000;
    public final static int DefNumRecs  = 100000;
    public final static int DefBatchSize = 1000;
    public final static int MaxNameLength = 32;

    public final static int MAX_IPV4_STRLEN = 15;
    public static String ADGENUsage = "\n" +
    "[-h] -d datasource_name [-s schema_file] [-i indelim] [-o outdelim]\n" +
    "[-n numrec] [-m] [-b batch_size] [-u url] [-U username] [-p password]\n" +
    "[-N] [-w numworkers] [-u url] [-H hostip ] [-P port] [-D database_name]\n" +
    "[-v] [tablename1 tablename2 ...]\n" +
    "\n" +
    "-h              : Print the help and exit\n" +
    "-s schema_file  : contains the table definition (present limitation, one file\n" +
    "                  per table. If absent, looks for <tablenameN>_<datasource>_create_ddl.sql\n" +
    "                  One of schema_file or tablenameN must be supplied.\n" +
    "                  The top three lines specify, column name, types, and sizes\n" +
    "                  name line should be prefixed with --CNL\n" +
    "                  type line should be prefixed with --CTL\n" +
    "                  size line should be prefixed with --CLL\n" +
    "                  types are ALCOR value types if they are not natively supported by\n" +
    "                  the datastore, at present they are:\n" +
    "                   string      -> pure lower case US-ASCII strings, correspond to JSON\n" +
    "                                  string and SQL VARCHAR\n" +
    "                   string(L)   -> pure lower case US-ASCII strings, correspond to JSON\n" +
    "                                  string and SQL CHAR(L)\n" +
    "                   ip          -> Random pick from IPv4 or IPV6 value\n" +
    "                   ipv4        -> IPv4 value\n" +
    "                   ipv6        -> IPv6 value\n" +
    "                   int         -> JSON/SQL integer value\n" +
    "-i indelim  : input delimiter character on name, type and size lines,\n" +
    "              defaults to whitespace\n" +
    "-o outdelim : output delimiter character in generated datafile,\n" +
    "              defaults to '|'\n" +
    "-n numrec   : number of data records to generate, defaults to " + DefNumRecs + "\n" +
    "-m          : do not write datafile, drive inserts with in memory records,\n" +
    "              defaults writing datafile, schema_file with last extension\n" +
    "              removed and .csv appended\n" +
    "-b batch_size : Use batch/bulk inserts if supported, defaults to\n" +
    "               " + DefBatchSize + ". Not supported yet\n" +
    "-u url        : Use url as JDBC connection string, if given, this\n" +
    "                is used instead of <datasource>/<host>:<port>/dbname\n" +
    "-U username   : Database usernae, depending on the database, this\n" +
    "                may be optional\n" +
    "-p password   : Database user password (like username, conditional)\n" +
    "-w numWorkers : Number of worker threads\n" +
    "-N            : No DDL (don't create tables), default false,\n" +
    "                if given, DELETEs all rows before each run\n" +
    "-v            : Verbose output\n" +
    "tablename1 tablename2 ... : read definitions from datasource\n" +
    "                specific versions, tablenameN-datasource-ddl.sql,\n" +
    "                or generic tablenameN-ddl.sql, and query from\n" +
    "                datasource-dml.sql, or query-dml.sql, NOT supported yet";

    // inputs to randomizers
    public final static String alpha = "qwertyuioplkjhgfdsazxcvbnm";
    public final static String alnum = "qazwsxedcrfvtgbyhnujmikolp0987654321";
    public final static String printable = "!1qazxsw@2#3edcvfr$45%tgbnhy^6&7ujm,<ki*8(ol.>/?:;p0)_-+={[]}";
    public final static String digits = "0182534769";
    public final static String hexdig = "0123456789abcdef";

    public static int alphaLen = alpha.length();
    public static int alnumLen = alnum.length();
    public static int printableLen = printable.length();
    public static int digitsLen = digits.length();
    public static int hexdigLen = hexdig.length();

    public static final String NamePrefix = "--CNL";
    public static final String TypePrefix = "--CTL";
    public static final String LenPrefix  = "--CLL";

    public static String dataFile;

    // all generated values are going to be unique?
    // perhaps, split it along data types?
    public static HashSet<String> stringHash = new HashSet<>();

    public static HashSet<Integer> intHash = new HashSet<>();

    public static HashSet<BigInteger> bigIntegerHash = new HashSet<>();
    public static Random nameRandom = new Random();
    public static Random longRandom = new Random();

    public static class CommandLine
    {
        public String   inputFileName = null;
        public String   inputDelim = " ";
        public String   outputDelim = "|";
        public String   dataSource = null;
        public String   dbName = null;
        public String   username = null;
        public String   password = null;
        public String   url = null;
        public String   runTitle = "";
        public List<String> tables = new ArrayList<>();
        public long      numRows = DefNumRecs;
        public int      batchSize = DefBatchSize;
        public int      numWorkers = 1;
        public boolean  batchMode = false;
        public boolean  writeCSV = true;
        public boolean  verboseMode = false;
        public boolean  noDDl = false;

        public CommandLine()
        {
        }
    }

    // public static CommandLine cmdLine = new CommandLine();

    public enum AclorType
    {
        AT_STRING,   // string
        AT_IP,
        AT_IPV4,
        AT_IPV6,
        AT_INT,
        AT_MAC
    }
    /*
    * Three extra lines at the top of the table definition file,
    * of the following form are required.
    * --CNM<space>column name1 | column name 2| ...
    * --CTP<space>application datatype of column 1 | ...
    * --CLN<space> length of the value of column1 | ...
    * application data type supplies the extra information needed to generate
    * data values which match the semantics of the column type when the underlying
    * data store doesn't support the application type directly, say, IPv6.
    * Application types:
    *   GID: alpha numeric character sequence, UNIQUE KEY
    *   NID: numeric id, UNIQUE KEY
    *   NAME: alhpa character sequence, people. places, things etc.
    *   IP  : IPv4 or IPv6
    *   IPv4: Just that
    *   IPv6: Just that
    *   MAC : MAC address
    *   JSON:   TODO
    *
    * CLN line specifies length in bytes/characters where applicable (names/alpha values), -1
    * indicates that it is not applicable.
     */
    public static class ColumnDef
    {
        public ColumnDef(String n)
        {
            name = new String(n);
        }
        public ColumnDef(String n, AclorType t)
        {
            name = new String(n);
            type = t;
        }
        public ColumnDef(String n, AclorType t, String l)
        {
            name = new String(n);
            type = t;
            length = new String(l);
        }

        public String   name;
        public AclorType type;
        public String   length;
    }

    public static class TableDef
    {
        public String tblName;
        public static List<ColumnDef> columns;

        public TableDef()
        {
        }

        public static void print()
        {
            System.out.println("TableDefinition");

            int i;
            for (i = 0; i < columns.size(); ++i) {
                System.out.print(columns.get(i).name + " ");
            }

            System.out.println();
            for (i = 0; i < columns.size(); ++i) {
                System.out.print(columns.get(i).type + " ");
            }
            System.out.println();
            for (i = 0; i < columns.size(); ++i) {
                System.out.print(columns.get(i).length + " ");
            }
            System.out.println();
        }

    }

    public static int getRandint()
    {
        int v;

        for (int k = 0; k < MaxHashIter; ++k){
            v = Math.abs(UUID.randomUUID().hashCode());
            if (!intHash.contains(v)) {
                intHash.add(v);
                return v;
            }
        }

        throw new AssertionError("Failed to generate unique value after " + MaxHashIter + " attempts");
    }

    public static long genRandLong()
    {
        return longRandom.nextLong();
    }

    public static BigInteger getRandBigInt()
    {
        return new BigInteger("123");
    }
    public static String genInt(int low, int high)
    {
        return "123";
    }
    public static int genInt()
    {
        return (int)longRandom.nextLong();
    }

    private static String genString(int minLength, int maxLength, String octets)
    {
        char[] v = new char[maxLength];
        byte[] b = new byte[maxLength];

        nameRandom.nextBytes(b);
        int octLen = octets.length();
        int p;

        while (true) {
            int i = nameRandom.nextInt(maxLength);
            for (p = 0; p < maxLength && i > 0; ++p, --i) {
                v[p] = octets.charAt(Math.abs(b[p] % octLen));
            }

            if (p >= minLength)
                break;
        }

        String s = String.copyValueOf(v, 0, p);
        v = null;
        return s;
    }

    // minimum 8
    public static String genName(int maxLength)
    {
       return genString(8, maxLength, alpha);
    }

    public static String genAlhpa(int maxLength)
    {
        return "123";
    }

    public static String genIPv4()
    {
        byte[] b = new byte[4];
        StringBuilder sb = new StringBuilder();

        longRandom.nextBytes(b);
        for (int i = 0, j = 0; i < 4; ++i) {
            int octet = Math.abs(b[i] % 255);
            sb.append(octet);
            if (i < 3)
                sb.append('.');
        }

        return sb.toString();
    }

    public static String genIPv6()
    {
        byte[] b = new byte[16];
        StringBuilder sb = new StringBuilder();

        longRandom.nextBytes(b);
        for (int i = 0, j = 0; i < 16; i += 2) {
            short sword = (short)((b[i] << 8) + b[i + 1]);
            sword = (short) Math.abs(sword % 65535);
            sb.append(String.format("%04x", sword));
            if (i < 14)
                sb.append(':');
        }

        return sb.toString();
    }

    public static String genMAC()
    {
        byte[] b = new byte[6];
        StringBuilder sb = new StringBuilder();

        longRandom.nextBytes(b);
        for (int i = 0, j = 0; i < 6; ++i) {
            int octet = Math.abs(b[i] % 255);
            sb.append(String.format("%02x", octet));
            if (i < 5)
                sb.append(':');
        }

        return sb.toString();
    }

    public static String genRangeDbl(int low, int high)
    {
        return "123.456";
    }

    public static String genDbl()
    {
        return "0.123";
    }

    public static StringBuffer getLine()
    {
        return new StringBuffer("empty");
    }

    public static void extractNames(String line, TableDef tdef)
    {
        String[] split = line.split("\\s+");
        tdef.columns = new ArrayList<>();

        for (int i = 1; i < split.length; ++i) {
            ColumnDef cdef = new ColumnDef(split[i].trim());
            tdef.columns.add(cdef);
        }
    }

    public static void extractTypes(String line, TableDef tdef)
    {
        String[] split = line.split("\\s+");

        String ctype = null;
        AclorType atype = null;
        for (int i = 1; i < split.length; ++i) {
            ctype = split[i].trim();
            switch (ctype.toLowerCase(Locale.ROOT)) {
                case "string":
                case "name":
                    atype = AclorType.AT_STRING;
                    break;
                case "ip":
                    atype = AclorType.AT_IP;
                    break;
                case "ip4":
                    atype = AclorType.AT_IPV4;
                    break;
                case "ip6":
                    atype = AclorType.AT_IPV6;
                    break;
                case "int":
                    atype = AclorType.AT_INT;
                    break;
                case "mac":
                    atype = AclorType.AT_MAC;
                    break;
                default:
                    System.out.println("Unknown type: " + ctype);
                    System.exit(1);
            }

            tdef.columns.get(i - 1).type = atype;
        }
    }

    public static void extractLengths(String line, TableDef tdef)
    {
        String[] split = line.split("\\s+");

        for (int i = 1; i < split.length; ++i)
            tdef.columns.get(i - 1).length = new String(split[i].trim());
    }

    public static TableDef getTableDef(String filename, boolean pickTableName)
    {
        TableDef tdef = null;

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            tdef = new TableDef();
            String line;
            int exCount = 0;
            while (exCount < 3 && (line = br.readLine()) != null) {
                if (line.startsWith(NamePrefix)) {
                    extractNames(line, tdef);
                    ++exCount;
                }
                else if (exCount == 1 && line.startsWith(TypePrefix)) {
                    extractTypes(line, tdef);
                    ++exCount;
                }
                else if (exCount == 2 && line.startsWith(LenPrefix)) {
                    extractLengths(line, tdef);
                    ++exCount;
                }
            }

            if (pickTableName) {
                // kludge!
                Pattern pat = Pattern.compile("\\s*create \\s*table \\s*.*", Pattern.CASE_INSENSITIVE);
                while ((line = br.readLine()) != null) {
                    Matcher mat = pat.matcher(line);
                    if (mat.matches()) {
                        String[] tokens = line.split("\\s+");
                        tdef.tblName = new String(tokens[2].trim());
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(-1);
        }

        return tdef;
    }

    public static String makeDataFileName(String inFile)
    {
        int i = inFile.lastIndexOf('.');
        if (i <= 0)
            i = inFile.length();
        StringBuilder sb = new StringBuilder(inFile.substring(0, i));
        sb.append(".csv");

        return sb.toString();
    }

    public static void processArgs(String[] args, CommandLine cmdLine)
    {
        if (args.length < 2) {
            System.out.println("ADGEN -s schema_file");
            System.exit(-1);
        }


        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "-h":
                    System.out.println(ADGENUsage);
                    System.exit(0);
                    break;
                case "-d":
                    cmdLine.dataSource = new String(args[++i]);
                    break;
                case "-D":
                    cmdLine.dbName = new String(args[++i]);
                    break;
                case "-s":
                    cmdLine.inputFileName = new String(args[++i]);
                    break;
                case "-i":
                    cmdLine.inputDelim = new String(args[++i]);
                    break;
                case "-o":
                    cmdLine.outputDelim = new String(args[++i]);
                    break;
                case "-n":
                    cmdLine.numRows = Integer.parseInt(args[++i]);
                    break;
                case "-b":
                    cmdLine.batchSize = Integer.parseInt(args[++i]);
                    cmdLine.batchMode = true;
                    break;
                case "-m":
                    cmdLine.writeCSV = false;
                    break;
                case "-u":
                    cmdLine.url = new String(args[++i]);
                    break;
                case "-U":
                    cmdLine.username = new String(args[++i]);
                    break;
                case "-p":
                    cmdLine.password = new String(args[++i]);
                    break;
                case "-N":
                    cmdLine.noDDl = true;
                    break;
                case "-w":
                    cmdLine.numWorkers = Integer.parseInt(args[++i]);
                    break;
                case "-v":
                    cmdLine.verboseMode = true;
                    break;
                default:
                    if (args[i].startsWith("-")) {
                        System.out.println("Unknown option: " + args[i]);
                        System.exit(-1);
                    }
                    break;
            }
        }

        if (cmdLine.verboseMode) {
            System.out.print("ADGEN Args: ");
            for (String s : args)
                System.out.print(s + " ");
            System.out.println();
        }
    }

    public static void genData(TableDef tdef, DataBuffer dbuf, long numRows) throws Exception
    {
        /**
         * TODO: Put it under a guard!
         */
        String dataFile = makeDataFileName(tdef.tblName);
        String outDelim = "|";
        FileWriter ofile = new FileWriter(dataFile);
        for (int i = 0; i < numRows; ++i) {
            ArrayList<String> row = new ArrayList<>();
            String value;
            StringBuilder csvLine = new StringBuilder();
            for (int j = 0; j < tdef.columns.size(); ++j) {
                switch (tdef.columns.get(j).type) {
                    case AT_INT:
                        value = String.valueOf(genInt());
                        break;
                    case AT_IP:
                        if (((i ^ j) & 0x1) == 0)
                            value = genIPv4();
                        else
                            value = genIPv6();
                        break;
                    case AT_IPV4:
                        value = genIPv4();
                        break;
                    case AT_IPV6:
                        value = genIPv6();
                        break;
                    case AT_MAC:
                        value = genMAC();
                        break;
                    case AT_STRING:
                        value = genName(MaxNameLength);
                        break;

                    default:
                        throw new Exception("Unexpected type");
                }
                row.add(value);
                csvLine.append(value);
                if (j < tdef.columns.size() - 1)
                   csvLine.append(outDelim);
            }
            dbuf.addRow(row);
            csvLine.append("\n");
            ofile.write(csvLine.toString());
        }

        ofile.close();
    }
}
