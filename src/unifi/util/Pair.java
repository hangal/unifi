/*
UniFi software.
Copyright [2001-2010] Sudheendra Hangal  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 
    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/

package unifi.util;

public class Pair<L, R> { 
    private final L left;
    private final R right;
    
    public R getRight() {
        return right;
    } 
    
    public L getLeft() {
        return left;
    }
    
    public Pair(final L left, final R right) {
        this.left = left;
        this.right = right;
    }    
    
    public static <A, B> Pair<A, B> create(A left, B right) {
        return new Pair<A, B>(left, right);
    } 
  
    public final boolean equals(Object o) {
        if (!(o instanceof Pair))
            return false;
 
        final Pair<?, ?> other = (Pair) o;
        return equal(getLeft(), other.getLeft()) && equal(getRight(), other.getRight());
    }
  
    public static final boolean equal(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        }
        return o1.equals(o2);
    }
    
    public int hashCode() {
        int hLeft = getLeft() == null ? 0 : getLeft().hashCode();
        int hRight = getRight() == null ? 0 : getRight().hashCode(); 
        return hLeft ^ hRight;
    }
}
