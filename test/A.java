public class A
{

    static int z, z1, z2, x5;
    int w, x1, x2, y, y1, y2;

    public static int foo (int x3, int x4)
    {
        int p = 0;

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

    public static void main (String args[])
    {
        A a = new A ();
        z = foo (a.x1, a.x2) + z1 - z2;
        int tmp;
        if (z > 1)
        {
            tmp = a.y1 - 2;
        }
        else
        {
            tmp = a.y2 - 1;

        }
        a.w = tmp * a.y2; // Multiplication should not have any effect
    }

}
