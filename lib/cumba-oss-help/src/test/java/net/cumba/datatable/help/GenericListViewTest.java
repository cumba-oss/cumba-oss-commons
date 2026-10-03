package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericListViewTest
{

    @Test
    void testSize()
    {
        GenericListView<String> view = new GenericListView<>(i -> "item" + i, 5);
        assertEquals(5, view.size());
    }


    @Test
    void testGet()
    {
        String[] data =
        {
                "a", "b", "c"
        };
        GenericListView<String> view = new GenericListView<>(i -> data[i], 3);
        assertEquals("a", view.get(0));
        assertEquals("b", view.get(1));
        assertEquals("c", view.get(2));
    }


    @Test
    void testGetOutOfBoundsNegative()
    {
        GenericListView<String> view = new GenericListView<>(_ -> "item", 3);
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(-1));
    }


    @Test
    void testGetOutOfBoundsAboveSize()
    {
        GenericListView<String> view = new GenericListView<>(_ -> "item", 3);
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(3));
    }


    @Test
    void testNegativeSizeThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> new GenericListView<>(_ -> "item", -1));
    }


    @Test
    void testZeroSize()
    {
        GenericListView<String> view = new GenericListView<>(_ -> "item", 0);
        assertEquals(0, view.size());
        assertTrue(view.isEmpty());
    }


    @Test
    void testIterator()
    {
        GenericListView<Integer> view = new GenericListView<>(i -> i * 2, 4);
        Iterator<Integer> it = view.iterator();
        assertTrue(it.hasNext());
        assertEquals(0, it.next());
        assertEquals(2, it.next());
        assertEquals(4, it.next());
        assertEquals(6, it.next());
        assertFalse(it.hasNext());
    }


    @Test
    void testIsUnmodifiable()
    {
        GenericListView<String> view = new GenericListView<>(_ -> "item", 3);
        assertThrows(UnsupportedOperationException.class, () -> view.add("x"));
        assertThrows(UnsupportedOperationException.class, () -> view.set(0, "x"));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(0));
    }


    @Test
    void testContains()
    {
        String[] data =
        {
                "a", "b", "c"
        };
        GenericListView<String> view = new GenericListView<>(i -> data[i], 3);
        assertTrue(view.contains("b"));
        assertFalse(view.contains("d"));
    }


    @Test
    void testStream()
    {
        GenericListView<Integer> view = new GenericListView<>(i -> i + 1, 3);
        List<Integer> collected = view.stream().toList();
        assertEquals(List.of(1, 2, 3), collected);
    }


    @Test
    void testSubList()
    {
        GenericListView<Integer> view = new GenericListView<>(i -> i * 10, 5);
        List<Integer> sub = view.subList(1, 3);
        assertEquals(2, sub.size());
        assertEquals(10, sub.get(0));
        assertEquals(20, sub.get(1));
    }


    @Test
    void testLargeView()
    {
        int size = 100_000;
        GenericListView<Long> view = new GenericListView<>(i -> (long) i, size);
        assertEquals(size, view.size());
        assertEquals(0L, view.get(0));
        assertEquals(99_999L, view.get(99_999));
    }
}
