#!/bin/csh -f
# script to generate count of params for each method in a library
# useful to look for methods with long arg lists to give as examples

# look for java.* javax.* in rt.jar
set jar=rt.jar
jar tvf $jar | grep '\.class$' | egrep ' java/| javax' | sort >! /tmp/P123
perl -spi.bak -e 's/.* //; s/\//./g;s/\.class$//' /tmp/P123

/bin/rm -f /tmp/Q123
touch /tmp/Q123

foreach i (`cat /tmp/P123`)
    javap $i >> /tmp/Q123
end

# count # of commas in each line, because #commas +1 is the # of args for long arglists
perl -ne '@a = split (/,/); print $#a . " " . $_; ' /tmp/Q123 | sort -n
