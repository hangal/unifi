# script to run unifi on itself

require "getopts.pl";
Getopts ('cbrdt:n');
# -c get from cvs
# -b run build
# -r run unifi
# -d run diff

# one time setup
sub setup {
  # where to find the Unifi binaries
  $UNIFI="/u/hangal/tmp/unifi";
  $BCEL="/u/hangal/bcel";
  $ENV{"CLASSPATH"} = "$UNIFI/classes/unifi.jar:$BCEL/bcel-5.2.jar";
  $ENV{"JAVA_HOME"} = "/space/software/jdk15";
  $ENV{"SHELL"} = "/bin/csh";
  $basedir = "/u/hangal/bddbddb";

  # project and classpaths
  $PROJECT = "bddbddb";
  $BUILD_CMD="mkdir -p classes; find . -name \\*.java > flist; javac -g -d classes `cat flist` >&! build.log; cd classes; jar cvf bddbddb.jar . >&! jar.log";
  $APP_HELPER_CLASSPATH = ".:javabdd.jar:javabdd-1.0b2.jar:jwutil-1.0.jar:jwutil.jar:weka.jar:jdom.jar";
  @jars_to_analyze = ("classes/bddbddb.jar");

  # cvs setup (if needed)
  # $ENV{"CVSROOT"} = ":ext:hangal\@salt:/space/cvsroot";
  $ENV{"CVS_RSH"} = "ssh";
}

# get and build a release, args are cvs options, and tag
sub get_cvs_release() {
    $cvs_options = $_[0];
    $tag = $_[1];

    $done_file = "cvs-co.done";

    print "checking out tag $tag ... ";
    # print "cvs options are: $cvs_options \n";

    $dir = "$basedir/$tag";
    mkdir ("$dir");
    chdir ("$dir") || die ("Unable to change dir: $dir");
    if (-e $done_file) { print "already built\n"; return; }

    $stat = system ("/bin/csh -fc 'cvs co $cvs_options $PROJECT >&! get-cvs.log'");
    if (! -d "$dir/$PROJECT") { print ("Unable to checkout $PROJECT from CVS, please see $dir/get_cvs.log\n"); return; } 
    if ($stat != 0) { print ("Unable to checkout $PROJECT from CVS, return code of cvs co is $stat. please see $dir/get_cvs.log\n"); return; } 
    system ("/bin/csh -fc 'date > $done_file'");
    print "done\n";
}

# run build on all tags
sub run_build() {
    $done_file = "build.done";
    $save_classpath = $ENV{"CLASSPATH"};
    $ENV{"CLASSPATH"} .= ":$APP_HELPER_CLASSPATH";
    foreach $tag(@_) {
        print "Building tag $tag ... ";
        $dir = "$basedir/$tag";
        chdir ("$dir") || die ("Unable to change dir: $dir");
        if (-e $done_file) { print "already built\n"; next; }
        system ("/bin/csh -fc 'cd $PROJECT; $BUILD_CMD'");
      
        # check if all the jars to analyze got built
        foreach $jar (@jars_to_analyze) {
            $target_jar = "$dir/$PROJECT/$jar";
            if (! -e $target_jar) { print ("Unable to build $target_jar for tag $tag, please check $dir/$PROJECT/build.log, etc.\n"); next; }
        }
        system ("/bin/csh -fc 'date > $done_file'");
        print "done\n";
    }
    $ENV{"CLASSPATH"} = $save_classpath;
}

# no args, run_unifi on all tags.
sub run_unifi() {
    foreach $tag (@_) {
        print ("Running unifi on tag $tag ... ");
        $done_file = "$basedir/$tag/unifi.done";
	if (-e "$done_file") { print "already run\n"; next; }

        $save_classpath = $ENV{"CLASSPATH"};
        $dir = "$basedir/$tag/$PROJECT";
        if (chdir ("$dir") == 0) { print ("Error, unable to chdir to $dir\n"); next; }
	# add all jars to analyze to the classpath
	foreach $jar (@jars_to_analyze) {
            $ENV{"CLASSPATH"} .=  ":$jar";
	}
        $ENV{"CLASSPATH"} .=  ":$APP_HELPER_CLASSPATH";


	# print "classpath is " . $ENV{"CLASSPATH"};
	$all_jars = join (" ", @jars_to_analyze);
	system ("/bin/csh -fc 'java -Xmx512m -Dunifi.write=$tag.units unifi.watch $all_jars >&! unifi.log'");
	if (! -e "$dir/$tag.units") { print ("Unable to run unifi, please see $dir/unifi.log\n"); next; }
        system ("/bin/csh -fc 'date > $done_file'");
	print "done\n";
        $ENV{"CLASSPATH"} = $save_classpath;
    }
}

# arg is @tags
sub run_diffs {
    chdir ($basedir);
    my @tags = @_;
    my $tag1 = shift @tags;
    while ($#tags >= 0) {
	my $tag2 = shift @tags;
        if (! -e "$basedir/$tag2/$PROJECT/$tag2.units") {
            print "Warning: no units file for tag $tag2\n";
            next;
        }
        
	print "diffing $tag1 and $tag2... ";
	# chdir ("$basedir/$tag1");
        $logfile = "diff.$tag1-$tag2.txt";
        system ("/bin/csh -fc 'java -Xmx512m unifi.diff $basedir/$tag1/$PROJECT/$tag1.units $basedir/$tag2/$PROJECT/$tag2.units >&! $logfile'");
        @log_lines = `cat $logfile`;

        @diff_lines = grep (/unit diff records/, @log_lines);
        if ($#diff_lines != 1) { print "SEVERE WARNING: diff records not as expected!\n"; }

        @zero_diff_lines = grep (/^0 unit diff records/, @log_lines);
        if ($#zero_diff_lines == 1) { print "No diffs\n"; unlink($logfile); }
        else {
            @nonzero_diff_lines = grep (/unit diff records/, @log_lines);
            foreach $line (@nonzero_diff_lines) { 
                chop ($line);
                $line =~ s/ .*//; # remove everything after first space, we just want the number
                print "$line "; 
            }
            print "diff records\n";
            print_source_diffs ($tag1, $tag2);
        }
       # system ("/bin/csh -fc 'cat $logfile'");
	$tag1 = $tag2;
    }
}

# given 2 flists, print the source diffs between them to the given $logfile.
sub list_diffs {
#    ($dir1, $dir2, @a, @b) = @_;
    my ($dir1, $dir2, $a_ref, $b_ref, $logfile) = @_;
    my @a = @$a_ref;
    my @b = @$b_ref;

    foreach $e (@a) { chop($e); $count_a{$e}++ }
    foreach $e (@b) { chop($e); $count_b{$e}++ }

    open(DIFF_FILE, ">> $logfile"); 
    print DIFF_FILE "\n\n\n----------------- Showing source diffs -------------\n";
    $count = 0;
    foreach $e (keys %count_a) {
        if ($count_b{$e} == 1) {
            # $e is a common file
            $output = `diff -w $dir1/$e $dir2/$e`;
            if ($? != 0) { # return status code is in $?
                $count ++;
                print DIFF_FILE "\n\n------------- Diff # " . $count . " in file $e --------------\n" . $output . "\n";
            }
        }
    }
    print DIFF_FILE "\n\n------------- $count source files were different --------------\n" . $output . "\n";

    foreach $e (keys %count_a) { if ($count_b{$e} != 1) { push (@a_not_b, $e); } } 
    if ($#a_not_b >= 0) { print DIFF_FILE "-----------\nFiles in $dir1 but not in $dir2:\n"; print DIFF_FILE join("\n", @a_not_b); }
    foreach $e (keys %count_b) { if ($count_a{$e} != 1) { push (@b_not_a, $e); } } 
    if ($#b_not_a >= 0) { print DIFF_FILE "-----------\nFiles in $dir2 but not in $dir1:\n"; print DIFF_FILE join("\n", @b_not_a); }
    close (DIFF_FILE);
}

sub print_source_diffs {
    my ($tag1, $tag2) = @_;
    my $dir1 = "$basedir/$tag1/$PROJECT";
    chdir ("$dir1");
    my @flist1 = `find . -name \\*.java`;
    my $dir2 = "$basedir/$tag2/$PROJECT";
    chdir ("$dir2");
    my @flist2 = `find . -name \\*.java`;
    chdir ("$basedir");

    # print "flist1 = @flist1";
    # print "flist2 = @flist2";
    $logfile = "diff.$tag1-$tag2.txt";
    &list_diffs($dir1, $dir2, \@flist1, \@flist2, $logfile);
}

&setup;
$last_tag_file = "$basedir/last.tag";
$last_tag = `cat $last_tag_file`;
chop($last_tag);
$this_tag = `date +%Y%m%d`;
chop ($this_tag);
print "diffing $last_tag and $this_tag";
@tags = ();
push (@tags, $last_tag);
push (@tags, $this_tag);
if (-e $this_tag/unifi.done) {
	echo $this_tag > $basedir/last.tag"
}

if (defined($opt_c)) {
    # @vc_dates = split (/,/, $opt_c);
    @vc_dates = @tags;
    print "dates to get releases for : " . join (" ", @vc_dates) . "\n";
    foreach (@vc_dates) {
       &get_cvs_release ("-D $_", $_);
       # push (@tags, $_);
    }
} elsif (defined($opt_t)) {
    @vc_tags = split (/,/, $opt_t);
    print "tags to get releases for : " . join (" ", @vc_tags) . "\n";
    foreach (@vc_tags) {
        # opt_n means don't get from CVS
        if (! defined ($opt_n)) {
            &get_cvs_release ("-r $_", $_);
        }
        push (@tags, $_);
    }
} 

# print "tags to be run on are : " . join (" ", @tags) . "\n";

if (defined($opt_b)) { &run_build(@tags); }
if (defined($opt_r)) { &run_unifi(@tags); }
if (defined($opt_d)) { &run_diffs(@tags); }

