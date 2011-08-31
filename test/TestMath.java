import java.util.*;
class TestMath {


double base1, base2;
double square1, square2;
double cube1, cube2;
double x, x1;

void m()
{
    base1 = Math.sqrt(square1);
    // base2 = base1 + 1;
    // square2 = base2 * base2;

    // cube1 = Math.pow (base1, 3);
    // cube2 = Math.pow (base2, 3);

    // double x = Math.pow (base1, 0.333);
    x = base1;
    x1 = Math.pow (x, 1);
    double one_fifth = Math.pow (x, 0.2);
    double one_third = Math.pow (x, 0.333);
    double one_tenth = Math.pow (x, 0.1);
    double one_eleventh = Math.pow (x, 0.099); // should not be recognized

    double third = Math.pow (x, 3);
    double hundredth = Math.pow (x, 100);

    double minus_third = Math.pow (x, -3);
    double minus_hundredth = Math.pow (x, -100);

    double minus_one_fifth = Math.pow (x, -0.2);
    double minus_one_third = Math.pow (x, -0.333);
    double minus_one_tenth = Math.pow (x, -0.1);
    double minus_one_eleventh = Math.pow (x, -0.099); // should not be recognized
}

}
