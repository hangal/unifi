/** simple test to check if clinit works */
class clinit {

static int x1, x2; 
static { x1 = 1; x2 = x1; } 
}

