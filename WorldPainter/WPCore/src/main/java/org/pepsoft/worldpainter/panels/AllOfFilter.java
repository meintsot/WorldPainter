package org.pepsoft.worldpainter.panels;

import org.pepsoft.worldpainter.operations.Filter;

import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * A filter that combines a number of subordinate filters, returning the lowest strength among them.
 * This implements AND semantics: the filter passes only if ALL sub-filters pass.
 * Used for "except on" multi-selection where painting should be blocked if the pixel
 * matches ANY of the exception items.
 */
public final class AllOfFilter implements Filter {
    public AllOfFilter(Collection<Filter> filters) {
        this(filters.toArray(new Filter[filters.size()]));
    }

    private AllOfFilter(Filter[] filters) {
        this.filters = filters;
    }

    public List<Filter> getFilters() {
        return unmodifiableList(asList(filters));
    }

    @Override
    public float modifyStrength(int x, int y, float strength) {
        if (strength <= 0.0f) {
            return 0.0f;
        }
        float minStrength = strength;
        for (Filter filter : filters) {
            float s = filter.modifyStrength(x, y, strength);
            if (s < minStrength) {
                minStrength = s;
                if (minStrength <= 0.0f) {
                    return 0.0f; // Short-circuit: can't go lower
                }
            }
        }
        return minStrength;
    }

    private final Filter[] filters;
}
