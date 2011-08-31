/* tests worklist by causing re-evaluation of m() after seeing plus1 */

class CS2 {

int a, b, c, d, e, f, g, h;

public int inc(int x)
{
    return x+1;
}

public void m()
{
    b = inc(a);
    d = inc(c);

    f = plus1(e);
    h = plus1(g);
}

public int plus1(int y)
{
    return y+1;
}

}
