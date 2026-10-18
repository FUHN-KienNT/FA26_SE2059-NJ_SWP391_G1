package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.StudentSubjectDTO.Offering;

class StudentSubjectServiceTest {
    @Test void rejectsInvalidIdsViewsAndOversizedSearchBeforeOpeningDatabase(){
        var service=new StudentSubjectService(()->{throw new AssertionError("No database access expected");});
        assertThrows(IllegalArgumentException.class,()->service.list(1,"foreign",""));
        assertThrows(IllegalArgumentException.class,()->service.list(1,"available","x".repeat(101)));
        assertThrows(IllegalArgumentException.class,()->service.detail(1,0));
        assertThrows(IllegalArgumentException.class,()->service.register(1,-1));
    }
    private Offering row(Instant now,boolean current,boolean active,boolean enabled,boolean enrolled,long seats){
        return new Offering(1,"SWP391","Project","Description","ACTIVE","TERM","Term",active,current,enabled,
                Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-02-01T00:00:00Z"),
                enrolled?"ACTIVE":null,null,null,null,null,seats,now);
    }
    @Test void registrationWindowIncludesOpenAndExcludesClose(){
        assertTrue(row(Instant.parse("2026-01-01T00:00:00Z"),true,true,true,false,1).canRegister());
        assertFalse(row(Instant.parse("2026-02-01T00:00:00Z"),true,true,true,false,1).canRegister());
        assertFalse(row(Instant.parse("2025-12-31T23:59:59Z"),true,true,true,false,1).canRegister());
    }
    @Test void activeCurrentOfferingSeatAndNotAlreadyEnrolledAreAllRequired(){
        var now=Instant.parse("2026-01-10T00:00:00Z");
        assertFalse(row(now,false,true,true,false,1).canRegister());
        assertFalse(row(now,true,false,true,false,1).canRegister());
        assertFalse(row(now,true,true,false,false,1).canRegister());
        assertFalse(row(now,true,true,true,true,1).canRegister());
        assertFalse(row(now,true,true,true,false,0).canRegister());
    }
}
