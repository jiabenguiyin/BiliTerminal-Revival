package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserInfoApiTest {
    @Test
    public void fullBirthdayIsKeptExactly() {
        assertEquals("2003-10-30", UserInfoApi.normalizeBirthday("2003-10-30"));
    }

    @Test
    public void plainSchoolNameIsKeptExactly() {
        assertEquals("Example University", UserInfoApi.readSchoolName("Example University"));
    }

    @Test
    public void chinaMidnightTimestampDoesNotMoveToPreviousDay() {
        assertEquals("2003-10-30", UserInfoApi.normalizeBirthday("1067443200"));
    }
}
