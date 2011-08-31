#!/bin/csh
set f = $1
grep Attribute $f > $f.attr
grep '(mag)' $f.attr | grep -v '(bits)' | sort -u > $f.mag
grep '(bits)' $f.attr | grep -v '(mag)' | sort -u > $f.bits
grep '(mag).*(bits)' $f.attr | sort -u > $f.mag-bits
grep 'Equalcompare' $f.attr | sort -u > $f.eq

