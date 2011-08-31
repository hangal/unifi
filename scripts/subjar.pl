#!/usr/bin/perl -w
use Cwd;

# first 2 args are prefix and root_dir
($root_dir, $prefix) = @ARGV;
chdir ($root_dir) || die;

$dir_str = `find $prefix -type d `;
@dir_list = split ('\n', $dir_str);

foreach $dir (@dir_list)
{
	&compute_filelist($dir);
}

foreach $dir (keys %filelist) 
{
	# print "directory $dir\n";
	# print "The files are $filelist{$dir}\n";

    $jar_name = $dir;
    $jar_name =~ s/^\.\///g; # strip leading ./
    $jar_name =~ s/\//./g;  # convert all / to .
    $cmd = "jar cvf $jar_name.jar $filelist{$dir}";
    print "cmd = $cmd\n";
    system ($cmd);
}

# takes in 1 argument
# fills in %filelist for that argument -> all files in that directory
sub compute_filelist
{
	@classfiles = ();   
    $dir = $_[0];
	opendir (DIR, $dir) || die;
	@file  = readdir (DIR);
	closedir(DIR);
	foreach $file_in_dir (@file)
	{
        $file_path = "$dir/$file_in_dir";
		push (@classfiles, $file_path) if (! -d $file_path);
	}

	$filelist{$_[0]}=join(' ', @classfiles);
}


print "DONE \n";
