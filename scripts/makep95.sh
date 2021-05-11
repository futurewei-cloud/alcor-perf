#! /bin/sh

# MIT License
# Copyright(c) 2020 Futurewei Cloud
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files(the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

if [ -z "$1" ]; then
	echo "Need one or more raw perf runlog file(s)"
	exit 1
fi

BASE=${1%.*}

GPS=gps-${BASE}-p95.gps

# trap "rm -f $GPS > /dev/null 2>&1" 0 1 2 3 4 5 6 7 8 10 11 12 13 14 15

cat <<-EOF > $GPS
set terminal svg size 900,600 dynamic mouse standalone enhanced linewidth 1.2
set title "P95 Latency"
set grid
show grid
set log y

set xlabel "Number of rows"
set ylabel "Time in Nano seconds (log scale)"
EOF

P95_DAT=latency-p95.dat.tmp
P95_TMP=latency-p95.tmp.tmp

NFILES=$#
i=1
PLOT=plot
if [ $NFILES -gt 1 ]; then
	CONT=",\\"
else
	CONT=""
fi

get_p95_value() {
	if [ -z "$1" ]; then
        echo "get_p95_value needs a filename"
		exit
	fi
    FILE=$1
	egrep '^Insert ' $FILE | cut -d' ' -f3 | sort -n > $P95_TMP
    THR_STATS=`wc -l $P95_TMP | cut -d' ' -f1`
	P95_POS=`echo ".95 * $THR_STATS" | bc`
	POSI=`expr "$P95_POS" : '\([1-9][0-9]*\)\.*'`
    POSR=`expr -- "$P95_POS" : '[0-9][0-9]*\.\([0-9][0-9]*\)'`
	if [ $POSR -gt 50 ]; then
		POSI=`expr $POSI + 1`
	fi
	if [ $POSI -gt $THR_STATS ]; then
		POSI=$THR_STATS
	fi
	# P95 value
	awk -vposi=$POSI '{if (NR >= posi) print}' $P95_TMP > $P95_DAT
}


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
    # P95 position (ins min)
    get_p95_value $f
    

#	cat <<-EOF >> $GPS
#	$PLOT '$f' using 1:2 with lp title "$LEG Insert Min", '' using 1:3 with lp title "$LEG Insert Max", '' using 1:4 with lp title "$LEG Query Min", '' using 1:5 with lp title "$LEG Query Max" $CONT
#EOF
	PLOT=""
	i=`expr $i + 1`
	if [ $i -eq $NFILES ]; then
		CONT=""
	fi
done

#gnuplot $GPS > ${BASE}.svg
