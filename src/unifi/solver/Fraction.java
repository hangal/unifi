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

package unifi.solver;
import java.io.Serializable;

import unifi.util.Util;

/** represents a reduced fraction, denominator shd never be -ve and always in reduced form,
i.e. numerator and denominator should not have a common factor
*/
public class Fraction implements Cloneable, Serializable {

int _numerator, _denominator;

// create a fraction n/d
public Fraction (int n, int d)
{
    _numerator = n;
    _denominator = d;
    reduce();
}

public int get_numerator()
{
	return _numerator;
}

public int get_denominator()
{
	return _denominator;
}

// create a fraction n/1
public Fraction (int n)
{
    _numerator = n;
    _denominator = 1;
}

public Object clone ()
{
    try {
        return super.clone();
    } catch (CloneNotSupportedException c) { Util.die (); return null; }
}

public void reduce ()
{
    // denom shd never be -ve
    if (_denominator < 0)
    {
        _denominator = - _denominator;
        _numerator = - _numerator;
    }

    for (int i = 2; i < _denominator; i++)
    {
        if ((_denominator%i == 0) && (_numerator%i == 0))
        {
            _denominator /= i;
            _numerator /= i;
            --i;
        }
    }
}

public void add (Fraction other)
{
    _numerator = _numerator * other._denominator +
                 other._numerator * _denominator;
    _denominator = _denominator * other._denominator;
    reduce();
}

public void subtract (Fraction other)
{
    _numerator = _numerator * other._denominator -
                 other._numerator * _denominator;
    _denominator = _denominator * other._denominator;
    reduce();
}

public void multiply (Fraction other)
{
    _numerator = _numerator * other._numerator;
    _denominator = _denominator * other._denominator;
    reduce();
}

public void divide (Fraction other)
{
    _numerator = _numerator * other._denominator;
    _denominator = _denominator * other._numerator;
    reduce();
}

public void invert (Fraction other)
{
    int tmp;
    tmp = _numerator;
    _numerator = _denominator;
    _denominator = tmp;
    reduce(); // should not be needed.
}

public boolean equals_zero()
{
    return (_numerator == 0);
}

public boolean equals(Object o)
{
    if (!(o instanceof Fraction))
        return false;

    Fraction other = (Fraction) o;
    return (_numerator == other._numerator && _denominator == other._denominator);
}

public String toString()
{
    if (_denominator == 1)
        return Integer.toString(_numerator);
    else
        return _numerator + "/" + _denominator;
}

}
