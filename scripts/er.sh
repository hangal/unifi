#!/bin/sh

i=0
for files1 in `ls -1 *.units`
do
     for files2 in `ls -1 *.units`
     do
	  java unifi.diff $files1 $files2 > jarfiles/merge.$i.log 2>&1
          i=$[$i + 1] 
     done
done
