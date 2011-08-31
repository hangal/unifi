#!/bin/sh

for (( i = 1; i < 10; i++ )) 
do
	for files in `ls -1 jakarta-tomcat-5.5.$i/common/lib/`
	do	
		echo "The file 55$i.$files"
		java -Dunifi.write=55$i.$files.units unifi.watch jakarta-tomcat-5.5.$i/common/lib/$files > 55$i.$files.log 2>&1
	done
done


#for (( i = 1; i < 9; i++ )) 
#do
#		for unitsfiles1 in `ls -1 *.units`
#		do
#			for (( j = $i+1; j < 9; j++ ))
#		do
#				for unitsfiles2 in `ls -1 *.units`
#				do
		#			java -Dunifi.gui=1 unifi.diff $unitsfiles1 $unitsfiles2
#					java unifi.diff $unitsfiles1 $unitsfiles2
#				done
#			done
#		done

#done
