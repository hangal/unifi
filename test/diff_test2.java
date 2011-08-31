public class diff_test2 {

int x;
Object o;

public void foo () 
{ 
    System.out.println (diff_test.a);
    System.out.println (diff_test.b);
    System.out.println (diff_test.c);

    diff_test.a = diff_test.b;
    diff_test.b = diff_test.c;

    // merges param 1 of m1, m2 and m3
    diff_test.m1(x);
    diff_test.m2(x);
    diff_test.m3(x);

    int t = diff_test.r1 () + diff_test.r2 () + diff_test.r3 ();

    o = diff_test.new_obj1();
    o = diff_test.new_obj2();
    o = diff_test.new_obj3();
}

}
