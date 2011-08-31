package test;

:qa
b

class X { int foo() { return 0; } }
class Y extends X { }
class Z extends Y { int foo() { return 0; } }

interface I1 { public int foo(); }
interface I2 { public int foo(); }

class CI implements I1, I2 { public int foo() { return 1; } }

class test1 {
    void m()
    {
        X x = new X();
        Y y = new Y();
        Z z = new Z();

        int x1 = x.foo();
        int y1 = y.foo();
        int z1 = y.foo();
    }
}

