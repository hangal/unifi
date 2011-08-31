#!/usr/bin/perl -w 

$jarstr = `ls -1 *.jar`;
@jars = split ('\n',$jarstr);

print "The contents $jarstr\n";
foreach $jarfiles (@jars)
{
	$cmd = "java -Dunifi.write=units/$jarfiles.units unifi.watch ./$jarfiles > units/$jarfiles.log 2>&1";
	print "$jarfiles \n";
	system ($cmd);
}
