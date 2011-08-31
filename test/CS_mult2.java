public class CS_mult2 {

int f, f1, f2, f3, f4;

// simple case for context-sensitive handling of multiply units 
// multiplies a context-sensitive parameter with a field.
int product (int a)
{ 
    return a * f;
}

void m()
{
    f1 = product (f2);
    f3 = product (f4);
}
}
