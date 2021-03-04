#! /bin/sh

if [ -z "$1" ]; then
	echo "Usage: `basename $0` CSV data file (space/tab delimited)
	need a data file with 3 columns:
	Nrows (K), Insert time (ns), Query time (ns)
	if the file is named qaz-wsx.csv, qaz will be datasource,
	wsx will be config mode"
	exit 1
fi

BASE=${1%.*}
DS=`expr "$1" : '\([a-z0-9A-Z][a-z0-9A-Z]*\)-.*'`
CF=`expr "$1" : '.*-\([a-z0-9A-Z][a-z0-9A-Z]*\).*'`

GPS=gps-${BASE}.gps

# trap "rm -f $GPS > /dev/null 2>&1" 0 1 2 3 4 5 6 7 8 10 11 12 13 14 15

cat <<-EOF > $GPS
set terminal svg size 900,600 dynamic mouse standalone enhanced linewidth 1.2
set grid
show grid
set log y

set xlabel "Number of rows"
set ylabel "Time in Nano seconds (log scale)"
EOF

NFILES=$#
i=1
PLOT=plot
if [ $NFILES -gt 1 ]; then
	CONT=",\\"
else
	CONT=""
fi

for f in $@; do
	HDR=`sed -n 's/#Run[\t ][\t ]*//p' $f`
	DSRC=`echo $HDR | awk '{print $1}'`
	NTHR=`echo $HDR | awk '{print $NF}'`
	LFLD=`echo $HDR | awk '{print NF}'`
	LFLD=`expr $LFLD - 1`
	if [ $LFLD -gt 2 ]; then
		LDESC=`echo $HDR | cut -d' ' -f2-$LFLD`
	else
		LDESC=
	fi
	LEG="$DSRC $LDESC $NTHR Client threads"
	cat <<-EOF >> $GPS
	$PLOT '< sed 1d $f' using 1:2 with lp title "${LEG} Inserts", '' using 1:3 with lp title "${LEG} Queries" $CONT
EOF
	PLOT=""
	i=`expr $i + 1`
	if [ $i -eq $NFILES ]; then
		CONT=""
	fi
done

gnuplot $GPS > ${BASE}.svg
