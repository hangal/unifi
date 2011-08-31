
require "getopts.pl";
Getopts ('d');

$basedir = "/net/monsoon/p1/iodine";
$last_tag_file = "/home/hangal/iodine/last.tag";
$UNIFI="/home/hangal/tmp/unifi";
$ENV{"JAVA_HOME"} = "/usr/dist/pkgs/java,v1.4.2/5.x-sun4";
$ENV{"SHELL"} = "/bin/csh";

sub setup {
  $ENV{"CVSROOT"} = "/home/hangal/GV-CVSTREE";
  $ENV{"CLASSPATH"} = "$UNIFI/classes/unifi.jar:/home/hangal/BCEL/bcel-5.1/bcel-5.1.jar:/net/sarod.india/export/home2/tools/pkgs/findbugs-0.8.2/lib/findbugs-ant.jar";
  $ENV{"CLASSPATH"} = $ENV{"CLASSPATH"} . ":$basedir/$this_tag/iodine/classes/iodine.jar";
}

sub get_release {
  mkdir ("$dir");
  chdir ("$dir") || die ("Unable to change dir: $dir");

  system ("/bin/csh -fc '/opt/sfw/bin/cvs co iodine >&! get-cvs.log'");
  if (! -d "$dir/iodine") { die ("Unable to checkout iodine from CVS, please see $dir/get_cvs.log"); } 

  system ("/bin/csh -fc 'cd iodine; /home/hangal/ant/bin/ant >&! build.log'");
  if (! -e "$dir/iodine/classes/iodine.jar") { die ("Unable to build iodine, please see $dir/iodine/build.log"); }
}

sub run_unifi {
  system ("/bin/csh -fc 'java -Xmx512m -Dunifi.write=iodine.units unifi.watch $basedir/$this_tag/iodine/classes/iodine.jar >&! unifi.log'");
  if (! -e "$dir/iodine.units") { die ("Unable to run unifi, please see $dir/unifi.log"); }
}

sub diff_units {
  system ("/bin/csh -fc 'java -Xmx512m unifi.diff $basedir/$this_tag/iodine.units $basedir/$prev_tag/iodine.units >&! unifi.diff.log; cat unifi.diff.log'");
}

$this_tag = `date +%Y%m%d`;
chop ($this_tag);
$dir = "$basedir/$this_tag";

&setup;
&get_release;
&run_unifi;

if (defined($opt_d)) {
  $prev_tag = `cat $last_tag_file`;
  chop ($prev_tag);
  &diff_units;
}

system ("echo $this_tag > $last_tag_file");
# check if modif time > process time
die ("Unable to update $last_tag_file") unless -M $last_tag_file < 0;

