/* tests CS relationships involving array length */

class CS4 {

int[] a1, a2;
int l1, l2;

public void test(int a[], int l)
{
    // unifi a.length with l
    int x = a.length + l;
}

public void m()
{
    // should unify a1.length with l1 and a2.length with l2
    test(a1, l1);
    test(a2, l2);
}

}
