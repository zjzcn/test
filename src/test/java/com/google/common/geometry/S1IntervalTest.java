/*
 * Copyright 2005 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.geometry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public strictfp class S1IntervalTest extends GeometryTestCase {

    private void intervalOps(S1Interval x, S1Interval y, String expectedRelation,
                             S1Interval expectedUnion, S1Interval expectedIntersection) {
        // Test all of the interval operations on the given pair of intervals.
        // "expected_relation" is a sequence of "T" and "F" characters corresponding
        // to the expected results of Contains(), InteriorContains(), Intersects(),
        // and InteriorIntersects() respectively.

        assertEquals(x.contains(y), expectedRelation.charAt(0) == 'T');
        assertEquals(x.interiorContains(y), expectedRelation.charAt(1) == 'T');
        assertEquals(x.intersects(y), expectedRelation.charAt(2) == 'T');
        assertEquals(x.interiorIntersects(y), expectedRelation.charAt(3) == 'T');

        // bounds() returns a const reference to a member variable, so we need to
        // make a copy when invoking it on a temporary object.
        assertEquals(expectedUnion, x.union(y));
        assertEquals(expectedIntersection, x.intersection(y));

        assertEquals(x.contains(y), x.union(y) == x);
        assertEquals(x.intersects(y), !x.intersection(y).isEmpty());

        if (y.lo() == y.hi()) {
            S1Interval r = x.addPoint(y.lo());
            assertEquals(expectedUnion, r);
        }
    }

    @Test
    public void testBasic() {
        // "Quadrants" are numbered as follows:
        // quad1 == [0, Pi/2]
        // quad2 == [Pi/2, Pi]
        // quad3 == [-Pi, -Pi/2]
        // quad4 == [-Pi/2, 0]

        // Constructors and accessors.
        S1Interval quad12 = new S1Interval(0, -S2.M_PI);
        assertEquals(quad12.lo(), 0.0, DEFAULT_EPSILON);
        assertEquals(quad12.hi(), S2.M_PI, DEFAULT_EPSILON);
        S1Interval quad34 = new S1Interval(-S2.M_PI, 0);
        assertEquals(quad34.lo(), S2.M_PI, DEFAULT_EPSILON);
        assertEquals(quad34.hi(), 0.0, DEFAULT_EPSILON);
        S1Interval pi = new S1Interval(S2.M_PI, S2.M_PI);
        assertEquals(pi.lo(), S2.M_PI, DEFAULT_EPSILON);
        assertEquals(pi.hi(), S2.M_PI, DEFAULT_EPSILON);
        S1Interval mipi = new S1Interval(-S2.M_PI, -S2.M_PI);
        assertEquals(mipi.lo(), S2.M_PI, DEFAULT_EPSILON);
        assertEquals(mipi.hi(), S2.M_PI, DEFAULT_EPSILON);
        S1Interval quad23 = new S1Interval(S2.M_PI_2, -S2.M_PI_2); // inverted
        assertEquals(quad23.lo(), S2.M_PI_2, DEFAULT_EPSILON);
        assertEquals(quad23.hi(), -S2.M_PI_2, DEFAULT_EPSILON);
        S1Interval quad1 = new S1Interval(0, S2.M_PI_2);

        // is_valid(), is_empty(), is_inverted()
        S1Interval zero = new S1Interval(0, 0);
        assertTrue(zero.isValid() && !zero.isEmpty() && !zero.isFull());
        S1Interval empty = S1Interval.empty();
        assertTrue(empty.isValid() && empty.isEmpty() && !empty.isFull());
        assertTrue(empty.isInverted());
        S1Interval full = S1Interval.full();
        assertTrue(full.isValid() && !full.isEmpty() && full.isFull());
        assertTrue(!quad12.isEmpty() && !quad12.isFull() && !quad12.isInverted());
        assertTrue(!quad23.isEmpty() && !quad23.isFull() && quad23.isInverted());
        assertTrue(pi.isValid() && !pi.isEmpty() && !pi.isInverted());
        assertTrue(mipi.isValid() && !mipi.isEmpty() && !mipi.isInverted());

        // GetCenter(), GetLength()
        assertEquals(quad12.getCenter(), S2.M_PI_2, DEFAULT_EPSILON);
        assertEquals(quad12.getLength(), S2.M_PI, DEFAULT_EPSILON);
        assertDoubleNear(new S1Interval(3.1, 2.9).getCenter(), 3.0 - S2.M_PI);
        assertDoubleNear(new S1Interval(-2.9, -3.1).getCenter(), S2.M_PI - 3.0);
        assertDoubleNear(new S1Interval(2.1, -2.1).getCenter(), S2.M_PI);
        assertEquals(pi.getCenter(), S2.M_PI, DEFAULT_EPSILON);
        assertEquals(pi.getLength(), 0.0, DEFAULT_EPSILON);
        assertEquals(mipi.getCenter(), S2.M_PI, DEFAULT_EPSILON);
        assertEquals(mipi.getLength(), 0.0, DEFAULT_EPSILON);
        assertEquals(Math.abs(quad23.getCenter()), S2.M_PI, DEFAULT_EPSILON);
        assertEquals(Math.abs(quad23.getLength()), S2.M_PI, DEFAULT_EPSILON);
        S1Interval quad123 = new S1Interval(0, -S2.M_PI_2);
        assertDoubleNear(quad123.getCenter(), 0.75 * S2.M_PI);
        assertDoubleNear(quad123.getLength(), 1.5 * S2.M_PI);
        assertTrue(empty.getLength() < 0);
        assertEquals(full.getLength(), 2 * S2.M_PI, DEFAULT_EPSILON);

        // Complement()
        assertTrue(empty.complement().isFull());
        assertTrue(full.complement().isEmpty());
        assertTrue(pi.complement().isFull());
        assertTrue(mipi.complement().isFull());
        assertTrue(zero.complement().isFull());
        assertTrue(quad12.complement().approxEquals(quad34));
        assertTrue(quad34.complement().approxEquals(quad12));
        S1Interval quad4 = new S1Interval(-S2.M_PI_2, 0);
        assertTrue(quad123.complement().approxEquals(quad4));
        S1Interval quad234 = new S1Interval(S2.M_PI_2, 0);

        // Contains(double), InteriorContains(double)
        assertTrue(!empty.contains(0) && !empty.contains(S2.M_PI) && !empty.contains(-S2.M_PI));
        assertTrue(!empty.interiorContains(S2.M_PI) && !empty.interiorContains(-S2.M_PI));
        assertTrue(full.contains(0) && full.contains(S2.M_PI) && full.contains(-S2.M_PI));
        assertTrue(full.interiorContains(S2.M_PI) && full.interiorContains(-S2.M_PI));
        assertTrue(quad12.contains(0) && quad12.contains(S2.M_PI) && quad12.contains(-S2.M_PI));
        assertTrue(quad12.interiorContains(S2.M_PI_2) && !quad12.interiorContains(0));
        assertTrue(!quad12.interiorContains(S2.M_PI) && !quad12.interiorContains(-S2.M_PI));
        assertTrue(quad23.contains(S2.M_PI_2) && quad23.contains(-S2.M_PI_2));
        assertTrue(quad23.contains(S2.M_PI) && quad23.contains(-S2.M_PI));
        assertTrue(!quad23.contains(0));
        assertTrue(!quad23.interiorContains(S2.M_PI_2) && !quad23.interiorContains(-S2.M_PI_2));
        assertTrue(quad23.interiorContains(S2.M_PI) && quad23.interiorContains(-S2.M_PI));
        assertTrue(!quad23.interiorContains(0));
        assertTrue(pi.contains(S2.M_PI) && pi.contains(-S2.M_PI) && !pi.contains(0));
        assertTrue(!pi.interiorContains(S2.M_PI) && !pi.interiorContains(-S2.M_PI));
        assertTrue(mipi.contains(S2.M_PI) && mipi.contains(-S2.M_PI) && !mipi.contains(0));
        assertTrue(!mipi.interiorContains(S2.M_PI) && !mipi.interiorContains(-S2.M_PI));
        assertTrue(zero.contains(0) && !zero.interiorContains(0));

        // Contains(S1Interval), InteriorContains(S1Interval),
        // Intersects(), InteriorIntersects(), Union(), Intersection()
        S1Interval quad2 = new S1Interval(S2.M_PI_2, -S2.M_PI);
        S1Interval quad3 = new S1Interval(S2.M_PI, -S2.M_PI_2);
        S1Interval pi2 = new S1Interval(S2.M_PI_2, S2.M_PI_2);
        S1Interval mipi2 = new S1Interval(-S2.M_PI_2, -S2.M_PI_2);

        intervalOps(empty, empty, "TTFF", empty, empty);
        intervalOps(empty, full, "FFFF", full, empty);
        intervalOps(empty, zero, "FFFF", zero, empty);
        intervalOps(empty, pi, "FFFF", pi, empty);
        intervalOps(empty, mipi, "FFFF", mipi, empty);

        intervalOps(full, empty, "TTFF", full, empty);
        intervalOps(full, full, "TTTT", full, full);
        intervalOps(full, zero, "TTTT", full, zero);
        intervalOps(full, pi, "TTTT", full, pi);
        intervalOps(full, mipi, "TTTT", full, mipi);
        intervalOps(full, quad12, "TTTT", full, quad12);
        intervalOps(full, quad23, "TTTT", full, quad23);

        intervalOps(zero, empty, "TTFF", zero, empty);
        intervalOps(zero, full, "FFTF", full, zero);
        intervalOps(zero, zero, "TFTF", zero, zero);
        intervalOps(zero, pi, "FFFF", new S1Interval(0, S2.M_PI), empty);
        intervalOps(zero, pi2, "FFFF", quad1, empty);
        intervalOps(zero, mipi, "FFFF", quad12, empty);
        intervalOps(zero, mipi2, "FFFF", quad4, empty);
        intervalOps(zero, quad12, "FFTF", quad12, zero);
        intervalOps(zero, quad23, "FFFF", quad123, empty);

        intervalOps(pi2, empty, "TTFF", pi2, empty);
        intervalOps(pi2, full, "FFTF", full, pi2);
        intervalOps(pi2, zero, "FFFF", quad1, empty);
        intervalOps(pi2, pi, "FFFF", new S1Interval(S2.M_PI_2, S2.M_PI), empty);
        intervalOps(pi2, pi2, "TFTF", pi2, pi2);
        intervalOps(pi2, mipi, "FFFF", quad2, empty);
        intervalOps(pi2, mipi2, "FFFF", quad23, empty);
        intervalOps(pi2, quad12, "FFTF", quad12, pi2);
        intervalOps(pi2, quad23, "FFTF", quad23, pi2);

        intervalOps(pi, empty, "TTFF", pi, empty);
        intervalOps(pi, full, "FFTF", full, pi);
        intervalOps(pi, zero, "FFFF", new S1Interval(S2.M_PI, 0), empty);
        intervalOps(pi, pi, "TFTF", pi, pi);
        intervalOps(pi, pi2, "FFFF", new S1Interval(S2.M_PI_2, S2.M_PI), empty);
        intervalOps(pi, mipi, "TFTF", pi, pi);
        intervalOps(pi, mipi2, "FFFF", quad3, empty);
        intervalOps(pi, quad12, "FFTF", new S1Interval(0, S2.M_PI), pi);
        intervalOps(pi, quad23, "FFTF", quad23, pi);

        intervalOps(mipi, empty, "TTFF", mipi, empty);
        intervalOps(mipi, full, "FFTF", full, mipi);
        intervalOps(mipi, zero, "FFFF", quad34, empty);
        intervalOps(mipi, pi, "TFTF", mipi, mipi);
        intervalOps(mipi, pi2, "FFFF", quad2, empty);
        intervalOps(mipi, mipi, "TFTF", mipi, mipi);
        intervalOps(mipi, mipi2, "FFFF", new S1Interval(-S2.M_PI, -S2.M_PI_2), empty);
        intervalOps(mipi, quad12, "FFTF", quad12, mipi);
        intervalOps(mipi, quad23, "FFTF", quad23, mipi);

        intervalOps(quad12, empty, "TTFF", quad12, empty);
        intervalOps(quad12, full, "FFTT", full, quad12);
        intervalOps(quad12, zero, "TFTF", quad12, zero);
        intervalOps(quad12, pi, "TFTF", quad12, pi);
        intervalOps(quad12, mipi, "TFTF", quad12, mipi);
        intervalOps(quad12, quad12, "TFTT", quad12, quad12);
        intervalOps(quad12, quad23, "FFTT", quad123, quad2);
        intervalOps(quad12, quad34, "FFTF", full, quad12);

        intervalOps(quad23, empty, "TTFF", quad23, empty);
        intervalOps(quad23, full, "FFTT", full, quad23);
        intervalOps(quad23, zero, "FFFF", quad234, empty);
        intervalOps(quad23, pi, "TTTT", quad23, pi);
        intervalOps(quad23, mipi, "TTTT", quad23, mipi);
        intervalOps(quad23, quad12, "FFTT", quad123, quad2);
        intervalOps(quad23, quad23, "TFTT", quad23, quad23);
        intervalOps(quad23, quad34, "FFTT", quad234, new S1Interval(-S2.M_PI, -S2.M_PI_2));

        intervalOps(quad1, quad23, "FFTF", quad123, new S1Interval(S2.M_PI_2, S2.M_PI_2));
        intervalOps(quad2, quad3, "FFTF", quad23, mipi);
        intervalOps(quad3, quad2, "FFTF", quad23, pi);
        intervalOps(quad2, pi, "TFTF", quad2, pi);
        intervalOps(quad2, mipi, "TFTF", quad2, mipi);
        intervalOps(quad3, pi, "TFTF", quad3, pi);
        intervalOps(quad3, mipi, "TFTF", quad3, mipi);

        S1Interval mid12 = new S1Interval(S2.M_PI_2 - 0.02, S2.M_PI_2 + 0.01);
        S1Interval mid23 = new S1Interval(S2.M_PI - 0.01, -S2.M_PI + 0.02);
        S1Interval mid34 = new S1Interval(-S2.M_PI_2 - 0.02, -S2.M_PI_2 + 0.01);
        S1Interval mid41 = new S1Interval(-0.01, 0.02);

        S1Interval quad2hi = new S1Interval(mid23.lo(), quad12.hi());
        S1Interval quad1lo = new S1Interval(quad12.lo(), mid41.hi());
        S1Interval quad12eps = new S1Interval(quad12.lo(), mid23.hi());
        S1Interval quadeps12 = new S1Interval(mid41.lo(), quad12.hi());
        S1Interval quad123eps = new S1Interval(quad12.lo(), mid34.hi());
        intervalOps(quad12, mid12, "TTTT", quad12, mid12);
        intervalOps(mid12, quad12, "FFTT", quad12, mid12);
        intervalOps(quad12, mid23, "FFTT", quad12eps, quad2hi);
        intervalOps(mid23, quad12, "FFTT", quad12eps, quad2hi);
        intervalOps(quad12, mid34, "FFFF", quad123eps, empty);
        intervalOps(mid34, quad12, "FFFF", quad123eps, empty);
        intervalOps(quad12, mid41, "FFTT", quadeps12, quad1lo);
        intervalOps(mid41, quad12, "FFTT", quadeps12, quad1lo);

        S1Interval quad2lo = new S1Interval(quad23.lo(), mid12.hi());
        S1Interval quad3hi = new S1Interval(mid34.lo(), quad23.hi());
        S1Interval quadeps23 = new S1Interval(mid12.lo(), quad23.hi());
        S1Interval quad23eps = new S1Interval(quad23.lo(), mid34.hi());
        S1Interval quadeps123 = new S1Interval(mid41.lo(), quad23.hi());
        intervalOps(quad23, mid12, "FFTT", quadeps23, quad2lo);
        intervalOps(mid12, quad23, "FFTT", quadeps23, quad2lo);
        intervalOps(quad23, mid23, "TTTT", quad23, mid23);
        intervalOps(mid23, quad23, "FFTT", quad23, mid23);
        intervalOps(quad23, mid34, "FFTT", quad23eps, quad3hi);
        intervalOps(mid34, quad23, "FFTT", quad23eps, quad3hi);
        intervalOps(quad23, mid41, "FFFF", quadeps123, empty);
        intervalOps(mid41, quad23, "FFFF", quadeps123, empty);

        // AddPoint()
        S1Interval r = S1Interval.empty();
        S1Interval res;
        res = r.addPoint(0);
        assertEquals(res, zero);

        res = r.addPoint(S2.M_PI);
        assertEquals(res, pi);

        res = r.addPoint(-S2.M_PI);
        assertEquals(res, mipi);

        res = r.addPoint(S2.M_PI);
        res = res.addPoint(-S2.M_PI);
        assertEquals(res, pi);

        res = res.addPoint(-S2.M_PI);
        res.addPoint(S2.M_PI);
        assertEquals(res, mipi);

        res = r.addPoint(mid12.lo());
        res = res.addPoint(mid12.hi());
        assertEquals(res, mid12);

        res = r.addPoint(mid23.lo());
        res = res.addPoint(mid23.hi());
        assertEquals(res, mid23);

        res = quad1.addPoint(-0.9 * S2.M_PI);
        res = res.addPoint(-S2.M_PI_2);
        assertEquals(res, quad123);

        r = S1Interval.full();
        res = r.addPoint(0);
        assertTrue(res.isFull());

        res = r.addPoint(S2.M_PI);
        assertTrue(res.isFull());

        res = r.addPoint(-S2.M_PI);
        assertTrue(res.isFull());

        // FromPointPair()
        assertEquals(S1Interval.fromPointPair(-S2.M_PI, S2.M_PI), pi);
        assertEquals(S1Interval.fromPointPair(S2.M_PI, -S2.M_PI), pi);
        assertEquals(S1Interval.fromPointPair(mid34.hi(), mid34.lo()), mid34);
        assertEquals(S1Interval.fromPointPair(mid23.lo(), mid23.hi()), mid23);

        // Expanded()
        assertEquals(empty.expanded(1), empty);
        assertEquals(full.expanded(1), full);
        assertEquals(zero.expanded(1), new S1Interval(-1, 1));
        assertEquals(mipi.expanded(0.01), new S1Interval(S2.M_PI - 0.01, -S2.M_PI + 0.01));
        assertEquals(pi.expanded(27), full);
        assertEquals(pi.expanded(S2.M_PI_2), quad23);
        assertEquals(pi2.expanded(S2.M_PI_2), quad12);
        assertEquals(mipi2.expanded(S2.M_PI_2), quad34);

        // ApproxEquals()
        assertTrue(empty.approxEquals(empty));
        assertTrue(zero.approxEquals(empty) && empty.approxEquals(zero));
        assertTrue(pi.approxEquals(empty) && empty.approxEquals(pi));
        assertTrue(mipi.approxEquals(empty) && empty.approxEquals(mipi));
        assertTrue(pi.approxEquals(mipi) && mipi.approxEquals(pi));
        assertTrue(pi.union(mipi).approxEquals(pi));
        assertTrue(mipi.union(pi).approxEquals(pi));
        assertTrue(pi.union(mid12).union(zero).approxEquals(quad12));
        assertTrue(quad2.intersection(quad3).approxEquals(pi));
        assertTrue(quad3.intersection(quad2).approxEquals(pi));
    }
}
