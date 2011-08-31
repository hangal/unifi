class CS6 {

int a1, a2;
static int GLOBAL;

public void m()
{
    // should unify a1 and a2 with GLOBAL
    test(a1);
    test(a2);
}

public void test(int x)
{
    // unifi a.length with l
    int y = x + GLOBAL;
}

}
