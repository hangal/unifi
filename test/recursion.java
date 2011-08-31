class recursion {

public int m(int a, float b, boolean c, byte d, long e)
{
    if (a == 0)
        return a;
    else
        return m(a-1, b, c, d, e);
}

}
