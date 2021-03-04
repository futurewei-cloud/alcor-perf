#! /bin/sh


JVM_OPTS=${JVM_OPTS:-}
PATH_SEP=
DBMS_LIBS=
DBMS_HOST=
DBMS_JARS=
BENCH_JAR=
BENCH_ARGS=
DBMS_URL=
CLASS_PATH=
CMD_DSNAME=
CMD_SCHFIL=
CMD_INDEL=
CMD_OUTDEL=
CMD_NUMROWS=
CMD_NOCSV=0
CMD_BATSIZ=
CMD_URL=
CMD_USRNAM=
CMD_PASSWD=
CMD_NODDL=0
CMD_NUMTHR=
CMD_HOST=
CMD_PORT=
CMD_DBNAME=
CMD_VERBOSE=0

PROG=`basename $0`

# Keep these in sync with options in Adgen.java
DefNumRecs=100000
DefBatchSize=1000

OPT_STR="hd:s:i:o:n:mbu:uU:p:Nw:H:P:D:v"
PROG_OPTS="\
    [-h] -d datasource_name [-s schema_file] [-i indelim] [-o outdelim]\n\
    [-n numrec] [-m] [-b batch_size] [-u url] [-U username] [-p password]\n\
    [-N] [-w numworkers] [-u url] [-H hostip ] [-P port] [-D database_name]\n\
    [-v] [tablename1 tablename2 ...]\n\
\n\
-h              : Print the help and exit\n\
-s schema_file  : contains the table definition (present limitation, one file\n\
                  per table. If absent, looks for <tablenameN>_<datasource>_create_ddl.sql\n\
		  One of schema_file or tablenameN must be supplied.\n\
                  The top three lines specify, column name, types, and sizes\n\
                  name line should be prefixed with --CNL\n\
                  type line should be prefixed with --CTL\n\
                  size line should be prefixed with --CLL\n\
                  types are ALCOR value types if they are not natively supported by\n\
                  the datastore, at present they are:\n\
                    string      -> pure lower case US-ASCII strings, correspond to JSON\n\
                                   string and SQL VARCHAR\n\
                    string(L)   -> pure lower case US-ASCII strings, correspond to JSON\n\
                                   string and SQL CHAR(L)\n\
                    ip          -> Random pick from IPv4 or IPV6 value\n\
                    ipv4        -> IPv4 value\n\
                    ipv6        -> IPv6 value\n\
                    int         -> JSON/SQL integer value\n\
-i indelim  : input delimiter character on name, type and size lines,\n\
              defaults to whitespace\n\
-o outdelim : output delimiter character in generated datafile,\n\
              defaults to '|'\n\
-n numrec   : number of data records to generate, defaults to $DefNumRecs\n\
-m          : do not write datafile, drive inserts with in memory records,\n\
              defaults writing datafile, schema_file with last extension\n\
              removed and .csv appended\n\
-b batch_size : Use batch/bulk inserts if supported, defaults to\n\
                $DefBatchSize. Not supported yet\n\
-u url      : Use url as JDBC connection string, if given, this\n\
              is used instead of <datasource>/<host>:<port>/dbname\n\
-U username : Database usernae, depending on the database, this\n\
              may be optional\n\
-p password : Database user password (like username, conditional)\n\
-w numWorkers : Number of worker threads\n\
-N            : No DDL (don't create tables), default false,\n\
                if given, DELETEs all rows before each run\n\
tablename1 tablename2 ...    -> read definitions from datasource\n\
                specific versions, tablenameN-datasource-ddl.sql,\n\
                or generic tablenameN-ddl.sql, and query from\n\
                datasource-dml.sql, or query-dml.sql. Not supported yet.\n\
"

ExitUsage() {
	echo "Usage: $PROG $PROG_OPTS"
	exit 0
}

ExitError() {
    if [ -n "$1" ]; then
        echo "$PROG: Error: $@"
    fi

    echo "Usage: $PROG $PROG_OPTS"
    exit 1
}


SetPathSep() {
	OSTYPE=`uname -s`
	if [ "$OSTYPE" = "Linux" ]; then
		PATH_SEP=":"
	else
		PATH_SEP=";"
	fi
}

SetIgniteEnv() {
	if [ -z "${IGNITE_HOME}" ]; then
		echo "Please set IGNITE_HOME and re-run"
		exit -1;
	fi

	DBMS_LIBS=${IGNITE_HOME}/libs
	DBMS_JARS="cache-api-1.0.0.jar ignite-core-2.9.1.jar annotations-16.0.3.jar ignite-shmem-1.0.0.jar"
	BENCH_JAR=IgniteJdbcBenchmark.jar
    if [ -z "$CMD_URL" ]; then
        if [ -z "$CMD_HOST" ]; then
            ExitError "Must specify one of url or host"
        fi
        DBMS_URL="jdbc:ignite:thin://$CMD_HOST"
    else
        DBMS_URL="$CMD_URL"
    fi
	JVM_OPTS="\
	    --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
	    --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
	    --add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
	    --add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
	    --add-exports=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
	    --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
	    --illegal-access=permit \
	    ${JVM_OPTS}"
}

SetPostgresEnv() {
	if [ -z "$PGJDBC_HOME" ]; then
		echo "Please set PGJDBC_HOME and rerun"
		exit 1
	fi
	DBMS_LIBS=${PGJDBC_HOME}
	DBMS_JARS=postgresql-42.2.18.jar
    if [ -z "$CMD_URL" ]; then
        if [ -z "$CMD_HOST" ]; then
            ExitError "Must specify one of url or host"
        fi
        DBMS_URL="jdbc:postgresql://$CMD_HOST"
        if [ -z "$CMD_PORT" ]; then
            DBMS_URL="${DBMS_URL}:5432"
        else
            DBMS_URL="${DBMS_URL}:${CMD_PORT}"
        fi
    else
        DBMS_URL="$CMD_URL"
    fi
    if [ -n "$CMD_DBNAME" ]; then
        DBMS_URL="${DBMS_URL}/${CMD_DBNAME}"
    else
        if [ -z "$CMD_USRNAM" ]; then
            ErrorExit "Postgres requires a database name, or username"
        fi
        DBMS_URL="${DBMS_URL}/${CMD_USRNAM}"
    fi
	BENCH_JAR=PostgresJdbcBenchmark.jar
}


processOpts() {
    while getopts "$OPT_STR" opt; do
        case $opt in
            h)  ExitUsage;;
            d)  CMD_DSNAME="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            s)  CMD_SCHFIL="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            i)  CMD_INDEL="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            o)  CMD_OUTDEL="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            n)  CMD_NUMROWS="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            m)  CMD_NOCSV=1; BENCH_ARGS="$BENCH_ARGS -${opt}";;
            b)  CMD_BATSIZ="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            u)  CMD_URL="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            U)  CMD_USRNAM="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            p)  CMD_PASSWD="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            N)  CMD_NODDL=1; BENCH_ARGS="${BENCH_ARGS} -${opt}";;
            w)  CMD_NUMTHR="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            H)  CMD_HOST="$OPTARG";;
            P)  CMD_PORT="$OPTARG";;
            D)  CMD_DBNAME="$OPTARG"; BENCH_ARGS="$BENCH_ARGS -${opt} $OPTARG";;
            v)  CMD_VERBOSE=1; BENCH_ARGS="$BENCH_ARGS -${opt}";;
            ?)
                ExitError "Unknown option";;
        esac
    done
}

SetEnv() {
    case "$CMD_DSNAME" in
        ignite) SetIgniteEnv;;
        postgres*) SetPostgresEnv;;
	    *)
		ExitError "Unsupported datasource $CMD_DSNAME";;
    esac 
}

# Main

if [ $# -lt 1 ]; then
	ExitError "Invalid syntax"
fi

processOpts $@
SetPathSep
SetEnv


if [ ! -r ${BENCH_JAR} -o ! -s ${BENCH_JAR} ]; then
	echo "${BENCH_JAR} is unreadable or empty"
	exit 1
fi
CLASS_PATH="./${BENCH_JAR}"
for j in `echo ${DBMS_JARS} | tr '[\t ]' '\n'`; do
	CLASS_PATH="${CLASS_PATH}${PATH_SEP}${DBMS_LIBS}/${j}"
done

MAIN_CLASS=`basename $BENCH_JAR .jar`
CMD="java ${JVM_OPTS} -cp ${CLASS_PATH} $MAIN_CLASS ${BENCH_ARGS} -u ${DBMS_URL}"

if [ $CMD_VERBOSE -eq 1 ]; then
	echo "Running: $CMD"
fi
eval "${CMD}"
