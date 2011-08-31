public class CS_mult {

int f1, f2, f3, f4, f5, f6;

// simplest case for context-sensitive handling of multiply units 
int product (int a, int b)
{ 
    return a * b;
}

void m()
{
    f3 = product (f1, f2);
    f6 = product (f4, f5);
}
}
