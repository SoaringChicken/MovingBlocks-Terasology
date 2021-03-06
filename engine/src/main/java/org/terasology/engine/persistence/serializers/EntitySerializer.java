// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.engine.persistence.serializers;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.engine.entitySystem.entity.internal.EntityInfoComponent;
import org.terasology.engine.entitySystem.entity.internal.EntityScope;
import org.terasology.engine.entitySystem.metadata.ComponentLibrary;
import org.terasology.engine.entitySystem.metadata.ComponentMetadata;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.prefab.PrefabManager;
import org.terasology.gestalt.entitysystem.component.Component;
import org.terasology.persistence.typeHandling.TypeHandlerLibrary;
import org.terasology.protobuf.EntityData;

import java.util.Map;
import java.util.Set;

import static org.terasology.protobuf.EntityData.Entity.Scope.CHUNK;
import static org.terasology.protobuf.EntityData.Entity.Scope.GLOBAL;
import static org.terasology.protobuf.EntityData.Entity.Scope.SECTOR;

/**
 * Provides the ability to serialize and deserialize entities to the EntityData.Entity proto buffer format.
 * <br><br>
 * As with the component serializer, a component id mapping can be provided to have components serialized against
 * ids rather than name strings.
 * <br><br>
 * It is also possible to set whether entity ids will be handled or ignored - if ignored then deserialized entities will
 * be given new ids.
 *
 */
public class EntitySerializer {
    private EngineEntityManager entityManager;
    private PrefabManager prefabManager;
    private ComponentLibrary componentLibrary;
    private ComponentSerializer componentSerializer;

    private ComponentSerializeCheck componentSerializeCheck = ComponentSerializeCheck.NullCheck.create();
    private boolean ignoringEntityId;

    /**
     * @param entityManager The entityManager that deserialized entities will be placed in.
     */
    public EntitySerializer(EngineEntityManager entityManager) {
        this(entityManager, entityManager.getComponentLibrary(), entityManager.getTypeSerializerLibrary());
    }

    public EntitySerializer(EngineEntityManager entityManager, ComponentLibrary componentLibrary, TypeHandlerLibrary typeHandlerLibrary) {
        this.entityManager = entityManager;
        this.prefabManager = entityManager.getPrefabManager();
        this.componentLibrary = componentLibrary;
        this.componentSerializer = new ComponentSerializer(componentLibrary, typeHandlerLibrary);
    }

    /**
     * @return Whether entity ids are being ignored during serialization/deserialization
     */
    public boolean isIgnoringEntityId() {
        return ignoringEntityId;
    }

    public boolean isUsingFieldIds() {
        return componentSerializer.isUsingFieldIds();
    }

    public void setUsingFieldIds(boolean usingFieldIds) {
        componentSerializer.setUsingFieldIds(usingFieldIds);
    }

    public void setComponentSerializeCheck(ComponentSerializeCheck check) {
        this.componentSerializeCheck = check;
    }

    /**
     * Sets whether entity ids should be ignored during serialization and deserialization.
     * If ignored, then they will not be stored in the protobuf, and deserialized entities will be given new ids. This
     * may break entity references, depending on how they are being handled (see: EntityRefTypeHandler)
     *
     * @param ignoringEntityId
     */
    public void setIgnoringEntityId(boolean ignoringEntityId) {
        this.ignoringEntityId = ignoringEntityId;
    }

    /**
     * Sets the mapping between component classes and the ids that are used for serialization
     *
     * @param table
     */
    public void setComponentIdMapping(Map<Class<? extends Component>, Integer> table) {
        componentSerializer.setIdMapping(table);
    }

    /**
     * @return An immutable copy of the component id mapping
     */
    public Map<Class<? extends Component>, Integer> getComponentIdMapping() {
        return componentSerializer.getIdMapping();
    }

    /**
     * Clears the mapping between component classes and ids. This causes components to be serialized with their component
     * class name instead.
     */
    public void removeComponentIdMapping() {
        componentSerializer.removeIdMapping();
    }

    /**
     * @param entityRef
     * @return The serialized entity
     */
    public EntityData.Entity serialize(EntityRef entityRef) {
        return serialize(entityRef, true, FieldSerializeCheck.NullCheck.<Component>newInstance());
    }

    /**
     * @param entityRef
     * @param deltaAgainstPrefab Whether the serialized entity should be a delta against its prefab (if any)
     * @return The serialized entity
     */
    public EntityData.Entity serialize(EntityRef entityRef, boolean deltaAgainstPrefab) {
        return serialize(entityRef, deltaAgainstPrefab, FieldSerializeCheck.NullCheck.<Component>newInstance());
    }

    /**
     * @param entityRef
     * @param fieldCheck Used to check whether each field in each component of the entity should be serialized.
     * @return The serialized entity
     */
    public EntityData.Entity serialize(EntityRef entityRef, FieldSerializeCheck<Component> fieldCheck) {
        return serialize(entityRef, true, fieldCheck);
    }

    /**
     * @param entityRef
     * @param fieldCheck Used to check whether each field in each component of the entity should be serialized.
     * @return The serialized entity
     */
    public EntityData.Entity serialize(EntityRef entityRef, boolean deltaAgainstPrefab, FieldSerializeCheck<Component> fieldCheck) {
        Prefab prefab = entityRef.getParentPrefab();
        if (prefab != null && deltaAgainstPrefab) {
            return serializeEntityDelta(entityRef, prefab, fieldCheck);
        } else {
            return serializeEntityFull(entityRef, fieldCheck);
        }
    }

    /**
     * @param entityData
     * @return The deserialized entity
     */
    public EntityRef deserialize(EntityData.Entity entityData) {
        Map<Class<? extends Component>, Component> componentMap = createInitialComponents(entityData);
        deserializeOntoComponents(entityData, componentMap);
        if (ignoringEntityId) {
            return entityManager.create(componentMap.values());
        } else {
            return entityManager.createEntityWithId(entityData.getId(), componentMap.values());
        }
    }

    /**
     * Creates the components for the entity being deserialized based on its prefab (if any)
     *
     * @param entityData
     * @return The mapping of components
     */
    private Map<Class<? extends Component>, Component> createInitialComponents(EntityData.Entity entityData) {
        Set<ComponentMetadata<?>> removedComponents = Sets.newHashSet();
        for (String removedComp : entityData.getRemovedComponentList()) {
            ComponentMetadata<?> removedMetadata = componentLibrary.resolve(removedComp);
            if (removedMetadata != null) {
                removedComponents.add(removedMetadata);
            }
        }

        Map<Class<? extends Component>, Component> componentMap = Maps.newHashMap();
        if (entityData.hasParentPrefab() && !entityData.getParentPrefab().isEmpty() && prefabManager.exists(entityData.getParentPrefab())) {
            Prefab prefab = prefabManager.getPrefab(entityData.getParentPrefab());
            for (Component component : prefab.iterateComponents()) {
                ComponentMetadata<?> metadata = componentLibrary.getMetadata(component);
                if (!removedComponents.contains(metadata)) {
                    componentMap.put(component.getClass(), componentLibrary.copy(component));
                }
            }
            componentMap.put(EntityInfoComponent.class, new EntityInfoComponent(prefab, true, prefab.isAlwaysRelevant()));
        }
        return componentMap;
    }

    /**
     * Deserializes the components from an EntityData onto a map of components
     *
     * @param entityData
     * @param componentMap
     */
    private void deserializeOntoComponents(EntityData.Entity entityData, Map<Class<? extends Component>, Component> componentMap) {
        EntityInfoComponent entityInfo = (EntityInfoComponent) componentMap.get(EntityInfoComponent.class);
        if (entityInfo == null) {
            entityInfo = new EntityInfoComponent();
            componentMap.put(EntityInfoComponent.class, entityInfo);
        }
        if (entityData.hasOwner()) {
            entityInfo.owner = entityManager.getEntity(entityData.getOwner());
        }
        if (entityData.hasAlwaysRelevant()) {
            entityInfo.alwaysRelevant = entityData.getAlwaysRelevant();
        }
        switch (entityData.getScope()) {
            case GLOBAL:
                entityInfo.scope = EntityScope.GLOBAL;
                break;
            case SECTOR:
                entityInfo.scope = EntityScope.SECTOR;
                break;
            case CHUNK:
                entityInfo.scope = EntityScope.CHUNK;
                break;
        }

        for (EntityData.Component componentData : entityData.getComponentList()) {
            ComponentMetadata<? extends Component> metadata = componentSerializer.getComponentMetadata(componentData);
            if (metadata == null || !componentSerializeCheck.serialize(metadata)) {
                continue;
            }

            Component existingComponent = componentMap.get(metadata.getType());
            if (existingComponent == null) {
                Component newComponent = componentSerializer.deserialize(componentData);
                componentMap.put(metadata.getType(), newComponent);
            } else {
                componentSerializer.deserializeOnto(existingComponent, componentData, FieldSerializeCheck.NullCheck.<Component>newInstance());
            }
        }
    }

    private EntityData.Entity serializeEntityFull(EntityRef entityRef, FieldSerializeCheck<Component> fieldCheck) {
        EntityData.Entity.Builder entity = EntityData.Entity.newBuilder();
        if (!ignoringEntityId) {
            entity.setId(entityRef.getId());
        }
        entity.setAlwaysRelevant(entityRef.isAlwaysRelevant());
        EntityRef owner = entityRef.getOwner();
        if (owner.exists()) {
            entity.setOwner(owner.getId());
        }
        EntityScope scope = entityRef.getScope();
        if (scope != null) {
            switch (scope) {
                case GLOBAL:
                    entity.setScope(GLOBAL);
                    break;
                case SECTOR:
                    entity.setScope(SECTOR);
                    break;
                case CHUNK:
                    entity.setScope(CHUNK);
                    break;

            }
        }

        for (Component component : entityRef.iterateComponents()) {
            if (!componentSerializeCheck.serialize(componentLibrary.getMetadata(component.getClass()))) {
                continue;
            }

            EntityData.Component componentData = componentSerializer.serialize(component, fieldCheck);
            if (componentData != null) {
                entity.addComponent(componentData);
            }
        }
        return entity.build();
    }

    private EntityData.Entity serializeEntityDelta(EntityRef entityRef, Prefab prefab, FieldSerializeCheck<Component> fieldCheck) {
        EntityData.Entity.Builder entity = EntityData.Entity.newBuilder();
        if (!ignoringEntityId) {
            entity.setId(entityRef.getId());
        }
        entity.setParentPrefab(prefab.getName());
        if (entityRef.isAlwaysRelevant() != prefab.isAlwaysRelevant()) {
            entity.setAlwaysRelevant(entityRef.isAlwaysRelevant());
        }
        EntityRef owner = entityRef.getOwner();
        if (owner.exists()) {
            entity.setOwner(owner.getId());
        }
        EntityScope scope = entityRef.getScope();
        if (scope != null) {
            switch (scope) {
                case GLOBAL:
                    entity.setScope(GLOBAL);
                    break;
                case SECTOR:
                    entity.setScope(SECTOR);
                    break;
                case CHUNK:
                    entity.setScope(CHUNK);
                    break;
            }
        }


        Set<Class<? extends Component>> presentClasses = Sets.newHashSet();
        for (Component component : entityRef.iterateComponents()) {
            if (!componentSerializeCheck.serialize(componentLibrary.getMetadata(component.getClass()))) {
                continue;
            }

            presentClasses.add(component.getClass());

            Component prefabComponent = prefab.getComponent(component.getClass());
            EntityData.Component componentData;
            if (prefabComponent == null) {
                componentData = componentSerializer.serialize(component, fieldCheck);
            } else {
                componentData = componentSerializer.serialize(prefabComponent, component, fieldCheck);
            }

            if (componentData != null) {
                entity.addComponent(componentData);
            }
        }
        for (Component prefabComponent : prefab.iterateComponents()) {
            ComponentMetadata<?> metadata = componentLibrary.getMetadata(prefabComponent.getClass());
            if (!presentClasses.contains(prefabComponent.getClass()) && componentSerializeCheck.serialize(metadata)) {
                // TODO: Use component ids here
                entity.addRemovedComponent(metadata.getId().toString());
            }
        }
        return entity.build();
    }

}
