class test_mult {

    int f_n, f_m; // dummies, just to make sure m and n are treated polymorphically
    // int a, b, b1, c, d, x;
    int a, b, z1;
    int z;
    public int m() 
    {
        /*
        b1 = b;
        a = b * c;
        x = a * b1;
        d = x / (b1);
        */
        z = a * b;
        z = a * b * b; // b must be dimensionless
        return f_m + a * b; // z must be same as a*b
       // z1 = z * z;
    }

    public int n()
    {
        return f_n + m() * m();
    }
}

