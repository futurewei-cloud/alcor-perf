#! /bin/sh

# call with host
CheckDelete() {
    SQLLINE=$IGNITE_HOME/bin/sqlline.sh
    HOST=$1
    while : ; do
        if [ $DS = "postgres" ]; then
            psql -q -t -d pkommoju -h $h -U pkommoju -w -f nodeinfo-sel.sql > sel.out
            if fgrep ' 0' sel.out > /dev/null 2>&1; then
                break
            fi
            echo "Waiting $h DELETE completion"
            psql -q -t -d pkommoju -h $h -U pkommoju -w -f nodeinfo-del.sql > sel.out
        elif [ $DS = "ignite" ]; then
            $SQLLINE -u jdbc:ignite:thin://$HOST --showHeader=false --verbose=false --silent=true --outputformat=csv -f nodeinfo-sel.sql > sel.out 2>&1
            echo "======"
            cat sel.out
            echo "======"
            if fgrep " not found; SQL statement:" sel.out > /dev/null 2>&1; then
                break
            elif fgrep "'0'" sel.out > /dev/null 2>&1; then
                break
            fi
            echo "Waiting $h DROP/DELETE completion"
            $SQLLINE -u jdbc:ignite:thin://$HOST --showHeader=false --verbose=false --silent=true --outputformat=csv -f nodeinfo-drp.sql > sel.out 2> /dev/null
        fi
        sleep 5
    done
}

if [ -z "$1" -o -z "$2" ]; then
    echo "Need datasource numworkers"
    exit 1
fi

DS=$1
shift
NUMT=$1
shift

if [ "$DS" = "postgres" ]; then
    true
elif [ "$DS" = "ignite" ]; then
    true
else
    echo "Don't know data source $DS"
    exit 1
fi

AGG_CSV="run-${DS}-${NUMT}-agg.csv"
OPS_CSV="run-${DS}-${NUMT}-ops.csv"
P95_CSV="run-${DS}-${NUMT}-p95.csv"
echo "#Run $DS $NUMT" > $AGG_CSV
echo "#Run $DS $NUMT" > $OPS_CSV
echo "#Run $DS $NUMT" > $P95_CSV

for NUMT in 10 20 30 40; do
	for n in 100000 1000000 2000000 5000000 10000000; do
        LOG_FILE="run-${DS}-${n}-${NUMT}-thrd-lat.log"
        nohup ./runbm.sh -w $NUMT -n $n $@ > $LOG_FILE 2>&1
        if ! fgrep '#METRIC' $LOG_FILE; then
            echo "Run $NUMT failed"
            # exit 1
        fi
        awk '/^#METRIC/ {print}' $LOG_FILE | \
                sed -e 's/^#METRIC//' -e '/^#[Rr][Uu][Nn]/d' >> run-${DS}-${NUMT}.csv
        INS_MIN=`awk '/^Insert / {print $3}' $LOG_FILE | sort -n | head -1`
        INS_MAX=`awk '/^Insert / {print $4}' $LOG_FILE | sort -n -r | head -1`
        QRY_MIN=`awk '/^Query / {print $3}' $LOG_FILE`
        QRY_MAX=`awk '/^Query / {print $4}' $LOG_FILE`
        echo "$n $INS_MIN $INS_MAX $QRY_MIN $QRY_MAX" >> $OPS_CSV
        echo

        for h in 10.213.43.163 10.213.43.164; do
            CheckDelete $h
        done
    done
done
