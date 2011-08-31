public class CS_mult1 {

int f1, f2, f3, f4, f5, f6;

// tests context sensitive mult units rippling through 2 levels of calls
int product (int a, int b)
{ 
    return a * b;
}

int indirect_product(int c, int d)
{
    return product(a, b);
}

void m()
{
    f3 = indirect_product (f1, f2);
    f6 = indirect_product (f4, f5);
}
}
