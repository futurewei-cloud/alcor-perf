#! /bin/sh

if [ -z "$1" ]; then
    echo "makejar needs Main class name as an argument.
        A <Main_class_Name>.cin file conating all class filenames
        in the same directory as <Main_class_Name> is expected as
        implicit input"
    exit 1
fi

MC=$1
CIN=${MC}.cin

trap "rm -f $CIN > /dev/null 2>&1" 0 1 2 3 4 5 6 7 8 10 11 12 13 14 15

ls -1 *.class > ${MC}.cin

if [ ! -r $CIN -o ! -s $CIN ]; then
    echo "$CIN is missing, unreadable or empty"
    exit 2
fi

JAR=/tmp/${MC}.jar

CFILES=""
for c in `cat $CIN`; do
    echo "Adding $c to ${JAR} contents"
    CFILES="${CFILES} $c"
done

jar -c --file ${JAR} -e ${MC} ${CFILES}

if [ $? -eq 0 -a -s ${JAR} ]; then
    echo "${JAR} created"
    exit 0
else
    echo "{JAR} creation failed"
    exit 1
fi
