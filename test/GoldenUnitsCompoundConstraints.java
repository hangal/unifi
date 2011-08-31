/** testing which compound constraints get retained when saving golden units */
class GoldenUnitsCompoundConstraints {
    
private int f0, f1, f2, f3;
private int f_rv;

public int a(int p0, int p1, int p2, int p3)
{

    f1 = p1;
    f2 = p2;
    f0 = p0;

    int y = 0, z = 0;
    int x = y * z; // should be dropped, local var mult 

    f0 = p1 * p2; // should be retained, all connected to field, therefore golden. 

    int rv = f_rv; 
    f1 = rv * rv; // should be retained, rv is connected to golden

    f1 = p3 * f2; // should be dropped, p3 is not golden

    return rv;
}

}
