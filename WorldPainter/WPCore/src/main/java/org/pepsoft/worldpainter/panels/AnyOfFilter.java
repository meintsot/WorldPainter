package org.pepsoft.worldpainter.panels;

import org.pepsoft.worldpainter.operations.Filter;

import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * A filter that combines a number of subordinate filters, returning the highest strength among them.
 * This implements OR semantics: the filter passes if ANY sub-filter passes.
 */
public final class AnyOfFilter implements Filter {
    public AnyOfFilter(Collection<Filter> filters) {
        this(filters.toArray(new Filter[filters.size()]));
    }

    private AnyOfFilter(Filter[] filters) {
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
        float maxStrength = 0.0f;
        for (Filter filter : filters) {
            float s = filter.modifyStrength(x, y, strength);
            if (s > maxStrength) {
                maxStrength = s;
                if (maxStrength >= strength) {
                    return strength; // Short-circuit: can't exceed input strength
                }
            }
        }
        return maxStrength;
    }

    private final Filter[] filters;
}
