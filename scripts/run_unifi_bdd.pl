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
  $UNIFI="/u/hangal/unifi";
  $BCEL="/u/hangal/bcel";
  $ENV{"CLASSPATH"} = "$UNIFI/classes/unifi.jar:$BCEL/bcel-5.2.jar";
  $ENV{"CVSROOT"} = ":pserver:anonymous\@bddbddb.cvs.sourceforge.net:/cvsroot/bddbddb";
  $ENV{"JAVA_HOME"} = "/space/software/jdk15";
  $ENV{"SHELL"} = "/bin/csh";
  $basedir = "/u/hangal/bddbddb";

  # project and classpaths
  $PROJECT = "bddbddb";
  $build_log_file = "build.log";
  $BUILD_CMD="mkdir -p classes; find . -name \\*.java > flist; javac -g -d classes `cat flist` >&! $build_log_file; cd classes; jar cvf bddbddb.jar . >&! jar.log";
  $APP_HELPER_CLASSPATH = ".:javabdd.jar:javabdd-1.0b2.jar:jwutil-1.0.jar:jwutil.jar:weka.jar:jdom.jar";
  @jars_to_analyze = ("classes/bddbddb.jar");

  # cvs setup (if needed)
  # $ENV{"CVSROOT"} = ":ext:hangal\@salt:/space/cvsroot";
  $ENV{"CVS_RSH"} = "ssh";

  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200408" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200409" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200410" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200411" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200412" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200501" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200502" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200503" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200504" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200505" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200506" . $i;
    push (@tags, $tag);
  }
  foreach $i ("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30") {
    $tag = "200507" . $i;
    push (@tags, $tag);
  }
}

# get and build a release, args are cvs options, and tag
sub get_cvs_release() {
    my $cvs_options = $_[0];
    my $tag = $_[1];

    my $done_file = "cvs-co.done";

    print "checking out tag $tag ... ";
    # print "cvs options are: $cvs_options \n";

    my $dir = "$basedir/$tag";
    mkdir ("$dir");
    chdir ("$dir") || die ("Unable to change dir: $dir");
    if (-e $done_file) { print "already checked out\n"; return; }
    unlink($done_file);

    $stat = system ("/bin/csh -fc 'cvs co $cvs_options $PROJECT >&! get-cvs.log'");
    if (! -d "$dir/$PROJECT") { print ("Unable to checkout $PROJECT from CVS, please see $dir/get_cvs.log\n"); return; } 
    if ($stat != 0) { print ("Unable to checkout $PROJECT from CVS, return code of cvs co is $stat. please see $dir/get_cvs.log\n"); return; } 
    system ("/bin/csh -fc 'date > $done_file'");
    print "done\n";
}

# run build on all tags
sub run_build() {
    $done_file = "build.done";
    $fail_file = "build.fail";
    $save_classpath = $ENV{"CLASSPATH"};
    $ENV{"CLASSPATH"} .= ":$APP_HELPER_CLASSPATH";
    foreach $tag(@_) {
        print "Building tag $tag ... ";
        $dir = "$basedir/$tag";
        chdir ("$dir") || die ("Unable to change dir: $dir");
        if (-e $done_file) { print "already built\n"; next; }

        unlink($done_file);
        unlink($fail_file);

        system ("/bin/csh -fc 'cd $PROJECT; $BUILD_CMD'");
      
        $b = "$basedir/$tag/$PROJECT/$build_log_file";
        # this works for javac, last line of the file has the word "error" in it
        $stat = system ("tail -1 $b | grep error");
        if ($stat == 0) { 
            system ("/bin/csh -fc 'date > $fail_file'");
            print ("Unable to build for tag $tag, please check $b, etc\n"); next; 
        }

        # check if all the jars to analyze got built
        foreach $jar (@jars_to_analyze) {
            $target_jar = "$dir/$PROJECT/$jar";
            if (! -e $target_jar) { 
                system ("touch $fail_file"); 
                print ("Unable to build $target_jar for tag $tag, please check $b\n"); next; 
            }
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
        $fail_file = "$basedir/$tag/unifi.fail";
	if (-e "$done_file") { print "already run\n"; next; }
        unlink($done_file);
        unlink($fail_file);

        $build_fail_file = "$basedir/$tag/build.fail";
        if (-e $build_fail_file) { print ("Build has failed, skipping\n"); next; }

        $build_pass_file = "$basedir/$tag/build.done";
        if (! -e $build_pass_file) { print ("Build has not been run, skipping\n"); next; }

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
	$status = system ("/bin/csh -fc 'java -Xmx512m -Dunifi.write=$tag.units -Dunifi.cs= unifi.drivers.Analyze $all_jars >&! unifi.log'");
	if (($status != 0) || (! -e "$dir/$tag.units")) { print ("Unable to run unifi, exit code $status, please see $dir/unifi.log\n"); system ("/bin/csh -fc 'date > $fail_file'"); next; }

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
        system ("/bin/csh -fc 'java -Xmx512m unifi.diff.Diff $basedir/$tag1/$PROJECT/$tag1.units $basedir/$tag2/$PROJECT/$tag2.units >&! $logfile'");
        @log_lines = `cat $logfile`;

        @diff_lines = grep (/unit diff records/, @log_lines);
        if ($#diff_lines != 1) { print "SEVERE WARNING: diff records not as expected!\n"; }

        @zero_diff_lines = grep (/^0 unit diff records/, @log_lines);
        if ($#zero_diff_lines == 1) { print "No diffs\n"; system ("mv $logfile no.$logfile"); }
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
    my %count_a = { };
    my %count_b = { };

    foreach $e (@a) {  chop($e); $e =~ s/^ *//; $e =~ s/ *$//; @tmp = (keys %count_a); $count_a{$e} = 1; @tmp = (keys %count_a); }
    foreach $e (@b) { chop($e); $e =~ s/^ *//; $e =~ s/ *$//; $count_b{$e}++; }

    open(DIFF_FILE, ">> $logfile"); 
    print DIFF_FILE "\n\n\n----------------- Showing source diffs -------------\n";
    $count = 0;
    foreach $e (keys %count_a) {
        if (defined($count_b{$e})) {
            # $e is a common file
            $output = `diff -w $dir1/$e $dir2/$e`;
            if ($? != 0) { # return status code is in $?
                $count ++;
                print DIFF_FILE "\n\n------------- Diff # " . $count . " in file $e --------------\n" . $output . "\n";
            }
        }
    }
    print DIFF_FILE "\n\n------------- $count source files were different --------------\n" . $output . "\n";

    my @a_not_b = ();
    my @b_not_a = ();

# NB: checking if defined(counta(x)) below is important
# otherwise the keys list has one junk entry
    foreach $x (keys %count_a) { 
        if (defined($count_a{$x}) && !defined($count_b{$x})) { push (@a_not_b, $x); } 
    }

    foreach $e (keys %count_b) { 
        if (defined($count_b{$e}) && !defined($count_a{$e})) { push (@b_not_a, $e); } 
    }

    if ($#a_not_b >= 0) { print DIFF_FILE "-----------\n" . ($#a_not_b+1) . "files in $dir1 but not in $dir2:\n"; print DIFF_FILE join("\n", @a_not_b); }
    if ($#b_not_a >= 0) { print DIFF_FILE "-----------\n". ($#b_not_a+1) . " files in $dir2 but not in $dir1:\n"; print DIFF_FILE join("\n", @b_not_a); }

    close (DIFF_FILE);
}

sub print_source_diffs {
    my ($tag1, $tag2) = @_;
    my $dir1 = "$basedir/$tag1/$PROJECT";
    chdir ("$dir1");
    my @flist1 = `find . -name \\*.java`;
    # print "# files1 = $#flist1";
    my $dir2 = "$basedir/$tag2/$PROJECT";
    chdir ("$dir2");
    my @flist2 = `find . -name \\*.java`;
    # print "# files2= $#flist2";
    chdir ("$basedir");

    # print "flist1 = @flist1";
    # print "flist2 = @flist2";
    $logfile = "diff.$tag1-$tag2.txt";
    &list_diffs($dir1, $dir2, \@flist1, \@flist2, $logfile);
}

&setup;
# $last_tag_file = "$basedir/last.tag";

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

