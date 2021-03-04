#! /bin/sh

if [ -z "$1" ]; then
	echo "Usage: `basename $0` CSV data file (space/tab delimited)
	need a data file with 3 columns:
	Nrows (K), Insert time MIN(ns), Insert time MAX, Query time (ns)
	Query time MAX.
	if the file is named qaz-wsx.csv, qaz will be datasource,
	wsx will be config mode"
	exit 1
fi

BASE=${1%.*}

GPS=gps-${BASE}.gps

# trap "rm -f $GPS > /dev/null 2>&1" 0 1 2 3 4 5 6 7 8 10 11 12 13 14 15

cat <<-EOF > $GPS
set terminal svg size 900,600 dynamic mouse standalone enhanced linewidth 1.2
set title "Plot of Latency Per Thread by Rows (times in nano seconds)\n\
Actual number of rows is adjusted to next multiple of threads"
set grid
show grid
set log y

set xlabel "Number of rows"
set ylabel "Time in Nano seconds (log scale)"
EOF

NFILES=$#
i=1
PLOT=splot
if [ $NFILES -gt 1 ]; then
	CONT=",\\"
else
	CONT=""
fi

for f in $@; do
	HDR=`head -1 $f`
	DSRC=`echo $HDR | cut -d' ' -f1`
	NROWS=`echo $HDR | cut -d' ' -f2`
	NTHR=`echo $HDR | cut -d' ' -f3`
	LEG="$DSRC $LDESC $NTHR Client threads"
	cat <<-EOF >> $GPS
	$PLOT '< sed "1,3d" $f' using 1:2:3 with lp title "$LEG Min", '' using 1:2:4 with lp title "$LEG Max", '' using 1:2:5 with lp title "$LEG Avg", '' using 1:2:6 with lp title "$LEG StdDev", '' using 1:2:7 with lp title "$LEG P25", '' using 1:2:8 with lp title "$LEG P50", '' using 1:2:9 with lp title "$LEG P75", '' using 1:2:10 with lp title "$LEG P95" $CONT
EOF
	PLOT=""
	i=`expr $i + 1`
	if [ $i -eq $NFILES ]; then
		CONT=""
	fi
done

gnuplot $GPS
