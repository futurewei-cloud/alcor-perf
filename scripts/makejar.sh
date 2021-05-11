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
