
# split a large jar and analyze by package
$root = $ARGV[0];
@jars = `find $root -name '*.jar'`;

foreach $jar (@jars) { 
    chop ($jar);
    #    print "extracting classes from $jar\n";
    print ".";
    @classes = `jar tvf $jar`;
    foreach (@classes) {
	chop;
	s/.* //; # stril till the last space
	next if ! /\//;
	next if ! /.class$/;

	$package = $_;
	$class = $_; 
	$package =~ s/\/[^\/]*$//; # remove evthg after the last /
	$class =~ s/.*\///; # remove evthg before the last /
	$class =~ s/\.class$//; # remove .class
	$class_list{$package} .= "";
	$class_list{$package} .= $class . " ";
	$jarfile{$package} = $jar;
    }
}

$n_pkgs = 0;
foreach $package (keys %class_list) {
    $n_pkgs++;
    @class_names = split (/ /, $class_list{$package});
    $n_classes += $#class_names;
}

print "\n$#jars jars, $n_pkgs packages, $n_classes classes\n";

# $package is package name separated by '/'
foreach $package (keys %class_list) {
    print "Running unifi on package $package from jar file $jarfile{$package}\n";
    @class_names = split (/ /, $class_list{$package});
    print "package $package from jarfile $jarfile{$package}, ", $#class_names + 1, " classes\n";
    print "  $class_list{$package}\n"; 


    $ENV{"CLASSPATH"} = "$jarfile{$package}" . ":" . $ENV{"CLASSPATH"};

    $pkg_dots = $package;
    $pkg_dots =~ s/\//./g;
# $pkg_dots is package name separated by '.'
    $cmd_line = "java -Xmx512m -Dunifi.write=$pkg_dots.UNITS unifi.watch ";
    foreach $class (@class_names) {
	$class =~ s/\$/\\\$/g;
	$cmd_line .= $pkg_dots . "." . $class . " "; # these are to protect against $'s in the class names
    }
    $cmd_line .= " >&! $pkg_dots.LOG";

    print ("cmd line is: $cmd_line\n");
    system ("/bin/csh -f -c '$cmd_line'");
#    system ("mv unifi.watch.log $pkg_dots.UNIFI_LOG");
}

