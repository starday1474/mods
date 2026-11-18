package dev.goddessrpg.tome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.goddessrpg.tome.client.TomePaging;
import org.junit.jupiter.api.Test;

class TomePagingTest {
    @Test
    void staysInsideTheBook() {
        assertEquals(4, TomePaging.PAGE_COUNT);
        assertEquals(0, TomePaging.prev(0));
        assertEquals(3, TomePaging.next(3));
        assertEquals(1, TomePaging.next(0));
        assertEquals(2, TomePaging.prev(3));
        assertEquals(0, TomePaging.clamp(-5));
        assertEquals(3, TomePaging.clamp(9));
    }

    @Test
    void scrollTurnsPages() {
        assertEquals(1, TomePaging.scroll(0, -1.0)); // wheel down = forward
        assertEquals(0, TomePaging.scroll(1, 1.0));
        assertEquals(3, TomePaging.scroll(3, -1.0));
        assertEquals(2, TomePaging.scroll(2, 0.0));
    }

    @Test
    void linesWindow() {
        assertEquals(0, TomePaging.clampScroll(-1, 10, 4));
        assertEquals(6, TomePaging.clampScroll(99, 10, 4));
        assertEquals(0, TomePaging.clampScroll(3, 2, 4));
    }
}
