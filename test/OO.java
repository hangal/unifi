// test for OO features

class OO { 
interface I0 { public void m(int i); }
interface I1 extends I0 { }
interface I2 { public void m(int i); }

class IImpl implements I1, I2 { // class which implements both I1 and I2

int z, z1, z2;
public void m(int zz) { z = zz; } // impl of I1 and I2, merges its param with z

// test function
public void x()
{
    I1 i1 = new IImpl();
    I0 i0 = new IImpl();
    i0.m(z1); // should merge z1 with I1.m which is merged with I0.m and Iimpl.im
    i1.m(z2);
    // z1, z2, z all merged though IImpl.im()'s parameter which is merged 
    // with I0.m, I1.m and I2.m's parameter
}

}

class A { public int f; 

public int x;
public int y1, y2, y3;

public void m(int y) { 
y1 = y; // y1 is a field so that m doesn't get recognized as a polymorphic method and get dropped
}

}

class B extends A {
public void m(int y) { }
}

void m() { 
    // field test
    A a =  new A();
    B b =  new B();
    int x1 = a.x; // bytecode refs A.x
    int x2 = b.x; // bytecode refs B.x
    // x and y merged because a.f and b.f get merged
}

void m1() {

    //method param test
    A a =  new A();
    B b =  new B();
    a.m(a.y2); // call to A.m()
    b.m(a.y3); // call to B.m()
    // A.y1 A.y2 A.y3 merged because a.m and b.m have the same munits
}
}
