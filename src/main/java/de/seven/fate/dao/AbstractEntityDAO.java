package de.seven.fate.dao;

import de.seven.fate.model.util.ClassUtil;
import de.seven.fate.model.util.CollectionUtil;
import org.apache.commons.beanutils.BeanUtils;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

import static org.apache.commons.lang3.Validate.notNull;


public abstract class AbstractEntityDAO<E extends IdAble<I>, I extends Serializable> {

    private static final Logger LOGGER = Logger.getLogger(AbstractEntityDAO.class.getName());

    @PersistenceContext(unitName = "job-portal")
    protected EntityManager em;

    private Class<E> entityType;

    /**
     * Persist takes an entity instance,
     * adds it to the context and makes that instance managed
     * (ie future updates to the entity will be tracked).
     *
     * @param entity
     */
    public void save(E entity) {
        notNull(entity);

        saveImpl(entity);
    }

    public void save(Collection<E> entities) {

        Optional.ofNullable(entities).orElse(Collections.emptyList()).forEach(this::save);
    }


    /**
     * Check for existing entity
     * and if found copy properties to existing one
     * and then merge else save entity
     *
     * @param entity
     * @return entity
     */
    public E saveOrUpdate(E entity) {
        notNull(entity);

        if (entity.getId() == null) {
            save(entity);

            return entity;
        }

        return saveOrUpdateImpl(entity);
    }

    protected E saveOrUpdateImpl(E entity) {
        notNull(entity);

        return saveOrUpdate(get(entity), entity);
    }

    public void saveOrUpdate(Collection<E> entities) {

        Optional.ofNullable(entities).orElse(Collections.emptyList()).forEach(this::saveOrUpdateImpl);
    }

    protected E saveOrUpdate(E recent, E entity) {
        notNull(entity);

        if (recent == null) {
            save(entity);
            return entity;
        } else if (recent.equals(entity)) {
            return recent;
        }

        updateProperties(recent, entity);

        return mergeImpl(recent);
    }

    public void updateProperties(E recent, E entity) {
        notNull(recent);
        notNull(entity);

        try {
            BeanUtils.copyProperties(recent, entity);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * @param id
     * @return founded entity or null
     */
    public E get(I id) {
        notNull(id);

        return em.find(entityType, id);
    }

    /**
     * @return founded entity by id or throw NoSuchEntityException
     */
    public E get(E entity) {
        notNull(entity);

        if (entity.getId() == null) {
            return null;
        }

        if (em.contains(entity)) {
            return entity;
        }

        return get(entity.getId());
    }

    /**
     * @param entity
     */
    public void remove(E entity) {
        notNull(entity);

        removeImpl(entity);
    }

    /**
     * remove entity by entityId
     *
     * @param entityId
     */
    public void remove(I entityId) {
        notNull(entityId);

        removeImpl(get(entityId));
    }

    /**
     * @param entities
     */
    public void remove(Collection<E> entities) {

        Optional.ofNullable(entities).orElse(Collections.emptyList()).forEach(this::remove);
    }

    public void removeAll() {
        remove(list());
    }

    public List<E> list() {
        return list(-1, -1);
    }

    public List<E> list(int startPosition, int maxResult) {

        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<E> criteria = builder.createQuery(entityType);
        Root<E> from = criteria.from(entityType);

        criteria.select(from);

        TypedQuery<E> query = em.createQuery(criteria);

        if (startPosition > -1) {
            query.setFirstResult(startPosition);
        }

        if (maxResult > -1) {
            query.setMaxResults(maxResult);
        }

        return query.getResultList();
    }

    public Long count() {

        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = builder.createQuery(Long.class);

        Root<E> from = criteria.from(entityType);

        criteria.select(builder.count(from));

        return em.createQuery(criteria).getSingleResult();
    }

    public Query createNamedQuery(String namedQuery, Object... params) {
        notNull(namedQuery);

        Query query = em.createNamedQuery(namedQuery);

        Map<Object, Object> parameters = CollectionUtil.createMap(params);

        for (Map.Entry<Object, Object> param : parameters.entrySet()) {
            query.setParameter(String.valueOf(param.getKey()), param.getValue());
        }

        return query;
    }

    @PostConstruct
    private void init() {

        entityType = ClassUtil.getGenericType(getClass());
    }

    protected void saveImpl(E entity) {
        assert entity != null;

        em.persist(entity);
    }

    protected void removeImpl(E entity) {
        assert entity != null;

        E e = get(entity);

        Optional.of(e).ifPresent(em::remove);
    }

    protected E mergeImpl(E entity) {
        assert entity != null;

        return em.merge(entity);
    }
}