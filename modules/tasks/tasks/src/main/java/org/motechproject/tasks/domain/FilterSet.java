package org.motechproject.tasks.domain;

import org.motechproject.mds.annotations.Cascade;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class FilterSet extends TaskConfigStep {
    private static final long serialVersionUID = 6046402871816204829L;

    @Field
    @Cascade(delete = true)
    private List<Filter> filters;

    public FilterSet() {
        this(null);
    }

    public FilterSet(List<Filter> filters) {
        this.filters = filters == null ? new ArrayList<Filter>() : filters;
    }

    public void addFilter(Filter filter) {
        if (filter != null) {
            filters.add(filter);
        }
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters.clear();

        if (filters != null) {
            this.filters = filters;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(filters);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        if (!super.equals(obj)) {
            return false;
        }

        final FilterSet other = (FilterSet) obj;

        return Objects.equals(this.filters, other.filters);
    }

    @Override
    public String toString() {
        return String.format("FilterSet{filters=%s} %s", filters, super.toString());
    }
}
