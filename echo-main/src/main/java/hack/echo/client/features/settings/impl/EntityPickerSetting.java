package hack.echo.client.features.settings.impl;

import hack.echo.client.utils.entities.EntityGroups;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class EntityPickerSetting extends RegistryPickerSetting<EntityType<?>> {

    public EntityPickerSetting(CharSequence name) {
        super(name, () -> BuiltInRegistries.ENTITY_TYPE);
    }

    public EntityPickerSetting(CharSequence name, Predicate<Object> dependency) {
        super(name, () -> BuiltInRegistries.ENTITY_TYPE, dependency);
    }

    @Override
    public String getTypeId() { return "ent"; }

    @Override
    public List<EntryGroup<EntityType<?>>> getGroups() {
        return EntityGroups.getAllGroups();
    }

    @Override
    public Function<EntityType<?>, String> getNameProvider() {
        return type -> type.getDescription().getString();
    }
}
