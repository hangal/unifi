/* tests CS relationships involving array elements. exercises worklist because m has to be re-evaluated after test. */

// should unifi x1, x2 and also y1, y2
class CS7 {

int x1, x2, y1, y2;
int[] xa = new int[1];
int[] ya = new int[1];

public void test(int x)
{
    // unifi a.length with l
    assign (xa, x1);
    assign (ya, y1);
    xa[0] = x2;
    ya[0] = y2;
}

public void assign(int a[], int e)
{
    // should unify a1 and a2 with GLOBAL
    a[0] = e;
}

}
