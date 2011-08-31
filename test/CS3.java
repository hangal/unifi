/* tests propagation through multiple methods */

class CS3 {

int a, b, c, d;

public void m()
{
    b = inc(a);
    d = inc(c);
}

public int inc(int x)
{
    return plus1(x);
}

public int plus1(int y)
{
    return y+1;
}

}
