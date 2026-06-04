package org.pepsoft.worldpainter;

import org.junit.Test;

import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OverlayTest {
    @Test
    public void defaultsAreSensible() {
        Overlay overlay = new Overlay(new File("plan.png"));
        assertEquals(0.0f, overlay.getRotation(), 0.0f);
        assertEquals(128, overlay.getThreshold());
        assertFalse(overlay.isInvert());
        assertNull(overlay.getCropRect());
    }

    @Test
    public void settersFirePropertyChanges() {
        Overlay overlay = new Overlay(new File("plan.png"));
        List<PropertyChangeEvent> events = new ArrayList<>();
        overlay.addPropertyChangeListener(events::add);

        overlay.setRotation(45.0f);
        overlay.setThreshold(90);
        overlay.setInvert(true);
        Rectangle crop = new Rectangle(10, 20, 100, 200);
        overlay.setCropRect(crop);

        assertEquals(45.0f, overlay.getRotation(), 0.0f);
        assertEquals(90, overlay.getThreshold());
        assertTrue(overlay.isInvert());
        assertEquals(crop, overlay.getCropRect());

        assertEquals(4, events.size());
        assertEquals("rotation", events.get(0).getPropertyName());
        assertEquals("threshold", events.get(1).getPropertyName());
        assertEquals("invert", events.get(2).getPropertyName());
        assertEquals("cropRect", events.get(3).getPropertyName());
    }

    @Test
    public void settingSameValueFiresNoEvent() {
        Overlay overlay = new Overlay(new File("plan.png"));
        List<PropertyChangeEvent> events = new ArrayList<>();
        overlay.addPropertyChangeListener(events::add);
        overlay.setRotation(0.0f); // already 0
        assertTrue(events.isEmpty());
    }
}
