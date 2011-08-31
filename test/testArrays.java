class testArrays {
public String s[] = {"abc", "def"};
public int ia[] = {1, 2, 3};

String s0, s1; // should not be merged, despite going into same array
int i1, i2, i3; // should be merged, going into same array
public void m()
{
    i1 = ia[0];
    i2 = ia[1];
    i3 = ia[2];

    s0 = s[0];
    s1 = s[1];
}

}
