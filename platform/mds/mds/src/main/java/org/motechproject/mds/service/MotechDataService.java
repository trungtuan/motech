package org.motechproject.mds.service;

import org.motechproject.mds.filter.Filter;
import org.motechproject.mds.query.QueryExecution;
import org.motechproject.mds.query.QueryParams;
import org.springframework.transaction.support.TransactionCallback;

import java.util.List;

/**
 * This is a basic service interface with CRUD operations. Mainly it is used as super interface to
 * create service interface related with the given entity schema in
 * {@link org.motechproject.mds.builder.EntityInfrastructureBuilder} but it can be also used by
 * other service interfaces inside this package.
 *
 * @param <T> the type of entity schema.
 */
public interface MotechDataService<T> {

    T create(T object);

    T retrieve(String primaryKeyName, Object value);

    List<T> retrieveAll();

    List<T> retrieveAll(QueryParams queryParams);

    T update(T object);

    void delete(T object);

    void delete(String primaryKeyName, Object value);

    T findTrashInstanceById(Object instanceId, Object entityId);

    void revertFromTrash(Object newInstance, Object trash);

    long count();

    Object getDetachedField(T instance, String fieldName);

    List<T> filter(Filter filter);

    List<T> filter(Filter filter, QueryParams queryParams);

    long countForFilter(Filter filter);

    void deleteAll();

    <R> R executeQuery(QueryExecution<R> queryExecution);

    T findById(Long id);

    <R> R doInTransaction(TransactionCallback<R> transactionCallback);
}
