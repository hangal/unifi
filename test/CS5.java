/* tests CS relationships involving array length. exercises worklist because m has to be re-evaluated after test. */

class CS5 {

int[] a1, a2;
int l1, l2;

public void m()
{
    // should unify a1.length with l1 and a2.length with l2
    test(a1, l1);
    test(a2, l2);
}

public void test(int a[], int l)
{
    // unifi a.length with l
    int x = a.length + l;
}

}
