#!/bin/sh

for files in `ls -1 jarfiles/tomcat-5.5.1`
do
	echo $files
        java -Dunifi.write=$files.units unifi.watch jarfiles/tomcat-5.5.1/$files> $files.log 2>&1
done

