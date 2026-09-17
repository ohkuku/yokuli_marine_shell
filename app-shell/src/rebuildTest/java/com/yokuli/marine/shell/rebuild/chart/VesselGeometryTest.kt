package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.bearing
import com.yokuli.marine.shell.rebuild.distance
import org.junit.Assert.*
import org.junit.Test

class VesselGeometryTest {
    private val point=GeoPoint(-36.84,174.77)

    @Test fun courseVectorUsesOneMinuteOfSogAndNeverHeading() {
        val vector=vesselCourseVector(MapVessel(point,90.0,true,0.0,6.0))!!
        assertEquals(185.2,distance(vector[0],vector[1]),0.001)
        assertEquals(90.0,bearing(vector[0],vector[1]),0.001)
    }
    @Test fun staleFixOrStationaryJitterDoesNotDrawCourseVector() {
        assertNull(vesselCourseVector(MapVessel(point,90.0,false,0.0,6.0)))
        assertNull(vesselCourseVector(MapVessel(point,90.0,true,0.0,0.1)))
    }
    @Test fun headingAloneDoesNotInventCourse() {
        assertNull(vesselCourseVector(MapVessel(point,null,true,90.0,6.0)))
    }
}
