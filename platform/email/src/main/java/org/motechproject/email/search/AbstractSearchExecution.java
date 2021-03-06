package org.motechproject.email.search;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.motechproject.commons.api.Range;
import org.motechproject.email.builder.EmailRecordSearchCriteria;
import org.motechproject.email.domain.DeliveryStatus;
import org.motechproject.mds.query.MatchesProperty;
import org.motechproject.mds.query.Property;
import org.motechproject.mds.query.QueryExecution;
import org.motechproject.mds.query.QueryUtil;
import org.motechproject.mds.query.RangeProperty;
import org.motechproject.mds.query.RestrictionProperty;
import org.motechproject.mds.query.SetProperty;
import org.motechproject.mds.util.InstanceSecurityRestriction;
import org.motechproject.mds.util.SecurityUtil;

import javax.jdo.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This is an abstract base for searches done in the email module.
 * This abstract base prepares the query for its implementations by setting
 * three conditions
 * @param <T>
 */
public abstract class AbstractSearchExecution<T> implements QueryExecution<T> {

    private static final String INITIAL_QUERY = "%s && %s";
    private static final int INITIAL_QUERY_LENGTH = INITIAL_QUERY.length();

    private final EmailRecordSearchCriteria criteria;

    public AbstractSearchExecution(EmailRecordSearchCriteria criteria) {
        this.criteria = criteria;
    }

    @Override
    public T execute(Query query, InstanceSecurityRestriction restriction) {
        List<Property> properties = new ArrayList<>();

        Range<DateTime> deliveryTimeRange = criteria.getDeliveryTimeRange();
        if (deliveryTimeRange == null) {
            deliveryTimeRange = new Range<>(new DateTime(0), new DateTime(Long.MAX_VALUE));
        }
        properties.add(new RangeProperty<>("deliveryTime", deliveryTimeRange));

        Set<DeliveryStatus> deliveryStatuses = criteria.getDeliveryStatuses();
        if (deliveryStatuses.isEmpty()) {
            deliveryStatuses = new HashSet<>(Arrays.asList(DeliveryStatus.values()));
        }
        properties.add(new SetProperty<>("deliveryStatus", deliveryStatuses));

        StringBuilder queryBuilder = new StringBuilder(INITIAL_QUERY);

        if (StringUtils.isNotEmpty(criteria.getToAddress())) {
            properties.add(new MatchesProperty("toAddress", criteria.getToAddress()));
            extendQueryWithOrClause(queryBuilder);
        }
        if (StringUtils.isNotEmpty(criteria.getFromAddress())) {
            properties.add(new MatchesProperty("fromAddress", criteria.getFromAddress()));
            extendQueryWithOrClause(queryBuilder);
        }
        if (StringUtils.isNotEmpty(criteria.getMessage())) {
            properties.add(new MatchesProperty("message", criteria.getMessage()));
            extendQueryWithOrClause(queryBuilder);
        }
        if (StringUtils.isNotEmpty(criteria.getSubject())) {
            properties.add(new MatchesProperty("subject", criteria.getSubject()));
            extendQueryWithOrClause(queryBuilder);
        }

        closeQuery(queryBuilder);

        if (restriction != null && !restriction.isEmpty()) {
            properties.add(new RestrictionProperty(restriction, SecurityUtil.getUsername()));
            queryBuilder.append(" && %s");
        }

        QueryUtil.useFilterFromPattern(query, queryBuilder.toString(), properties);

        return execute(query, properties);
    }

    protected abstract T execute(Query query, List<Property> properties);

    protected void extendQueryWithOrClause(StringBuilder queryBuilder) {
        if (queryBuilder.length() == INITIAL_QUERY_LENGTH) {
            queryBuilder.append(" && (%s");
        } else {
            queryBuilder.append(" || %s");
        }
    }

    protected void closeQuery(StringBuilder queryBuilder) {
        if (queryBuilder.length() != INITIAL_QUERY_LENGTH) {
            queryBuilder.append(')');
        }
    }

    protected EmailRecordSearchCriteria getCriteria() {
        return criteria;
    }
}
