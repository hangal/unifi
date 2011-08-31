public class diff_test1 {

int x, y;
Object o, o1;
public void foo () 
{ 
    System.out.println (diff_test.a);
    System.out.println (diff_test.b);
    System.out.println (diff_test.c);

    diff_test.a = diff_test.b;

    diff_test.m1 (x);
    diff_test.m2 (x);
    diff_test.m3 (y);

    int t = diff_test.r1() + diff_test.r2();
    int v = diff_test.r3();

    o = diff_test.new_obj1();
    o = diff_test.new_obj2();
    o1 = diff_test.new_obj3();
}

}
