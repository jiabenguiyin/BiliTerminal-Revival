package com.RobinNotBad.BiliClient.util;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class MySpaceOrderTest {
    @Test public void olderOrderKeepsChosenPositionsAndAppendsNewActions() {
        List<MySpaceOrder.Item> items = MySpaceOrder.parse("account_switch;favorite;history");
        assertEquals("account_switch", items.get(0).key);
        assertEquals("favorite", items.get(1).key);
        assertEquals("history", items.get(2).key);
        assertEquals(MySpaceOrder.parse("").size(), items.size());
        assertTrue(MySpaceOrder.encode(items).contains("experience_record"));
    }
    @Test public void unknownAndDuplicateKeysCannotHideOrDuplicateActions() {
        List<MySpaceOrder.Item> items = MySpaceOrder.parse(" ;unknown;favorite;favorite; follow ;unknown");
        assertEquals("favorite", items.get(0).key);
        assertEquals("follow", items.get(1).key);
        HashSet<String> keys = new HashSet<>();
        for (MySpaceOrder.Item item : items) assertTrue(keys.add(item.key));
        assertEquals(MySpaceOrder.parse("").size(), keys.size());
    }
    @Test public void savedOrderRoundTripsAndEmptyRestoresDefault() {
        List<MySpaceOrder.Item> items = MySpaceOrder.parse("logout;account_switch");
        assertEquals(MySpaceOrder.encode(items), MySpaceOrder.encode(MySpaceOrder.parse(MySpaceOrder.encode(items))));
        assertEquals("edit_profile", MySpaceOrder.parse("").get(0).key);
    }
}
