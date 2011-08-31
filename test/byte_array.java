// return value is a byte array - there was a bug in this
// causing infinite recursion
class byte_array {

static int idx;
static byte barray[];
static byte element;

static byte[] foo ()
{
    byte[] a = new byte[2];
    return a;
}

public static void main (String a[])
{
    barray = foo();
    barray[idx] = 0;
    element = barray[0];
}

}
