package com.RobinNotBad.BiliClient.util;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageUnreadStateTest {
    @Test public void noCountExistsUntilThisProcessHasConfirmedIt() {
        MessageUnreadState s=new MessageUnreadState();s.reset(42);assertEquals(0,s.count(42));
    }
    @Test public void freshRealUnreadIsRetainedWithinTheAccount() {
        MessageUnreadState s=new MessageUnreadState();assertTrue(s.complete(s.begin(42),42,3));assertEquals(3,s.count(42));
    }
    @Test public void newerConfirmedSnapshotInvalidatesTheSlowStartupResult() {
        MessageUnreadState s=new MessageUnreadState();MessageUnreadState.Check old=s.begin(42);s.complete(s.begin(42),42,0);
        assertFalse(s.complete(old,42,3));assertEquals(0,s.count(42));
    }
    @Test public void aNewCheckAfterConfirmedZeroCanStillReportRealNewMessages() {
        MessageUnreadState s=new MessageUnreadState();s.complete(s.begin(42),42,0);
        assertTrue(s.complete(s.begin(42),42,2));assertEquals(2,s.count(42));
    }
    @Test public void olderRequestsCannotOverwriteTheNewestCheck() {
        MessageUnreadState s=new MessageUnreadState();MessageUnreadState.Check old=s.begin(42),now=s.begin(42);
        assertFalse(s.complete(old,42,3));assertTrue(s.complete(now,42,1));assertEquals(1,s.count(42));
    }
    @Test public void switchingAccountsRejectsThePreviousAccountsRequest() {
        MessageUnreadState s=new MessageUnreadState();MessageUnreadState.Check old=s.begin(42);
        assertFalse(s.complete(old,43,3));assertEquals(0,s.count(43));
    }
    @Test public void switchingAwayAndBackDoesNotReviveAnOldRequest() {
        MessageUnreadState s=new MessageUnreadState();MessageUnreadState.Check old=s.begin(42);s.reset(0);s.begin(43);s.reset(0);s.begin(42);
        assertFalse(s.complete(old,42,3));assertEquals(0,s.count(42));
    }
    @Test public void anOldFailureCannotEraseNewerUnread() {
        MessageUnreadState s=new MessageUnreadState();MessageUnreadState.Check old=s.begin(42);s.complete(s.begin(42),42,0);
        assertTrue(s.complete(s.begin(42),42,4));assertFalse(s.fail(old,42));assertEquals(4,s.count(42));
    }
    @Test public void eachResultCanBeAppliedOnlyOnce() {
        MessageUnreadState s=new MessageUnreadState();MessageUnreadState.Check check=s.begin(42);
        assertTrue(s.complete(check,42,2));assertFalse(s.complete(check,42,3));assertEquals(2,s.count(42));
    }
    @Test public void loggedOutChecksAreNeverAccepted() {
        MessageUnreadState s=new MessageUnreadState();assertNull(s.begin(0));assertFalse(s.complete(null,0,3));assertEquals(0,s.count(0));
    }
    @Test public void aFreshFailureOrInvalidCountCannotProduceAPhantomBadge() {
        MessageUnreadState s=new MessageUnreadState();assertTrue(s.fail(s.begin(42),42));assertEquals(0,s.count(42));
        assertTrue(s.complete(s.begin(42),42,-3));assertEquals(0,s.count(42));
    }
    @Test public void processResetDropsPreviouslyConfirmedCounts() {
        MessageUnreadState s=new MessageUnreadState();s.complete(s.begin(42),42,3);s.reset(42);assertEquals(0,s.count(42));
    }
    @Test public void oneFailedEndpointRetainsItsPreviousConfirmedUnread() {
        MessageUnreadState s=new MessageUnreadState();s.complete(s.begin(42),42,2,3);
        assertTrue(s.complete(s.begin(42),42,1,null));assertEquals(4,s.count(42));
        assertTrue(s.complete(s.begin(42),42,null,0));assertEquals(1,s.count(42));
    }
    @Test public void aTimeoutDoesNotMeanPreviouslyConfirmedUnreadWasRead() {
        MessageUnreadState s=new MessageUnreadState();s.complete(s.begin(42),42,2,3);
        assertTrue(s.fail(s.begin(42),42));assertEquals(5,s.count(42));
        s.complete(s.begin(42),42,0,0);assertEquals(0,s.count(42));
    }
    @Test public void aNewAccountsFailedEndpointCannotInheritThePreviousAccountsCount() {
        MessageUnreadState s=new MessageUnreadState();s.complete(s.begin(42),42,2,3);
        s.complete(s.begin(43),43,null,1);assertEquals(1,s.count(43));assertEquals(0,s.count(42));
    }
}
