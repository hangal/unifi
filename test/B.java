public class B
{

    static long z, z1, z2, x5;
    long w, x1, x2, y, y1, y2;
    int x6, x7, x8;
    int y3, y4, y5;

    public static int foo (long x3, long x4)
    {
        long p = 0;

        while (z > 0)
        {
            if (z > 1)
            {
                p = x3 + 1;
            }
            else
            {
                p = x4 + 1;
            }
        }

        x5 = p;
        return 2;
    }

    public static int l2i (long y5, int y6)
    {
        return (int) (y5 + y6);
    }

    public static void main (String args[])
    {
        B b = new B ();
        z = foo (b.x1, b.x2) + z1 - z2;
        long tmp;
        if (z > 1)
        {
            tmp = b.y1 - 2;
        }
        else
        {
            tmp = b.y2 - 1;
        }
        b.w = ( (int) tmp * (int) b.y2); // Multiplication should not have any effect

        b.x6 = (int) (b.x1 + b.x2);
        b.x1 = b.x7 + b.x8;

        b.y3 = l2i (b.y1, b.y4);
    }

}
