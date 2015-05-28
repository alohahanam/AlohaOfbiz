/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.entity.cache;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.transaction.UserTransaction;

import org.ofbiz.base.util.Debug;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.transaction.TransactionListener;

public class EntityListCache extends AbstractEntityConditionCache<Object, List<GenericValue>> implements TransactionListener {

    public static final String module = EntityListCache.class.getName();
    private final ConcurrentHashMap<UserTransaction, Map<Object, List<GenericValue>>> txCacheMap = new ConcurrentHashMap<UserTransaction, Map<Object, List<GenericValue>>>();

    public EntityListCache(String delegatorName) {
        super(delegatorName, "entity-list");
    }

    public List<GenericValue> get(String entityName, EntityCondition condition) {
        return this.get(entityName, condition, null);
    }

    public List<GenericValue> get(String entityName, EntityCondition condition, List<String> orderBy) {
        ConcurrentMap<Object, List<GenericValue>> conditionCache = getConditionCache(entityName, condition);
        if (conditionCache == null) return null;
        Object orderByKey = getOrderByKey(orderBy);
        List<GenericValue> valueList = conditionCache.get(orderByKey);
        if (valueList == null) {
            // the valueList was not found for the given ordering, so grab the first one and order it in memory
            Iterator<List<GenericValue>> it = conditionCache.values().iterator();
            if (it.hasNext()) valueList = it.next();

            if (valueList != null) {
                // Does not need to be synchronized; if 2 threads do the same ordering,
                // the result will be exactly the same, and won't actually cause any
                // incorrect results.
                valueList = EntityUtil.orderBy(valueList, orderBy);
                conditionCache.put(orderByKey, valueList);
            }
        }
        return valueList;
    }

    public void put(String entityName, EntityCondition condition, List<GenericValue> entities) {
        this.put(entityName, condition, null, entities);
    }

    public List<GenericValue> put(String entityName, EntityCondition condition, List<String> orderBy, List<GenericValue> entities) {
        ModelEntity entity = this.getDelegator().getModelEntity(entityName);
        if (entity.getNeverCache()) {
            Debug.logWarning("Tried to put a value of the " + entityName + " entity in the cache but this entity has never-cache set to true, not caching.", module);
            return null;
        }
        for (GenericValue memberValue : entities) {
            memberValue.setImmutable();
        }
        Map<Object, List<GenericValue>> conditionCache = getOrCreateConditionCache(entityName, getFrozenConditionKey(condition));
        return conditionCache.put(getOrderByKey(orderBy), entities);
    }

    public List<GenericValue> remove(String entityName, EntityCondition condition, List<String> orderBy) {
        return super.remove(entityName, condition, getOrderByKey(orderBy));
    }

    public static final Object getOrderByKey(List<String> orderBy) {
        return orderBy != null ? (Object) orderBy : "{null}";
    }

    @Override
    public void update(UserTransaction tx, EventType notificationType) {
        /*
        switch (notificationType) {
            case BEGIN:
                txCacheMap.put(tx, new HashMap<GenericPK, GenericValue>());
                break;
            case COMMIT:
                Map<Object, List<GenericValue>> tempCache = txCacheMap.remove(tx);
                if (tempCache != null) {
                    for (Map.Entry<GenericPK, GenericValue> entry : tempCache.entrySet()) {
                        GenericPK pk = entry.getKey();
                        GenericValue value = entry.getValue();
                        UtilCache<GenericPK, GenericValue> entityCache = getOrCreateCache(pk.getEntityName());
                        entityCache.put(pk, value);
                    }
                }
                break;
            case ROLLBACK:
                txCacheMap.remove(tx);
        }
        */
    }
}