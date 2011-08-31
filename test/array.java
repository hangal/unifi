class array
{
static int x1 = 10, x2 = 3, x4;
static int z1 = 2, z2 = 1;

public static void test_array_indexes (String args[])
{
    // array indexes x1, x2, x4, need to merge, 
    // but arrays y1 and y2 themselves don't
    int y1[] = new int[x1];
    y1[x2] = 0; // unifies x1 and x2
    int x3 = x2 + 1; // d(x1) = d(x2) = d(x3) = d (y1.length)
    x4 = x3 + 1; // d(x1) = d(x2) = d(x3) = d (y1.length)
    int y2[] = new int[x4]; // d(y2.length) = d(x3); d(y2) = d(y1);
}

static int elt1, elt2, elt3, elt4;
static int[] y1, y2;

public static void test_array_elements()
{
    y1 = new int[2];
    y2 = new int[2];
    y1[0] = elt1;
    y2[0] = elt2; 
    elt2 = elt1+1; // d(elt1) = d(elt2) but d(y1) NOT = d(y2)

    int z1[] = new int[2];
    int z2[] = new int[2];
    z1[0] = elt3;
    if (System.getProperty("") == null)
        z2[0] = elt4;
    else
        z1 = z2; // d(z1) = d(z2); also d(elt3) = d(elt4)
}

/*
public static void test_objs (String args[])
{
    Object t1[] = new Object[z1];
    t1[z2] = null;
    int z3 = z2 + 1;
    Object t2[] = new Object[z3];
    t2[z1] = new Object();
}
*/
}
