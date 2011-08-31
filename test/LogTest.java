import java.lang.Math;
import org.apache.commons.logging.*;


class LogTest {
public static Log log = LogFactory.getLog(LogTest.class);
static int a, b, c;
static int x = 10, y = 9, z;

/* a polymorphic method */
public static int plus1(int p)
{
    return p+1;
}

public static void main(String args[])
{
    for (int i = 0; i < 100; i++)
    {
        /* some computations which capture that a, b, c all have the same unit */
        a = i*2;
        b = a + 1;
        c = a+b;

        /* x, y, z all have the same unit; note that unifications are performed through array indices and polymorphic calls */
        int ax[] = new int[x];
        ax[y] = 1;
        z = plus1(y);

        log.info ("i=" + i + " a=" + a + " b="+b);
        log.info ("x=" + x + " y=" + y);
    }
}
}
