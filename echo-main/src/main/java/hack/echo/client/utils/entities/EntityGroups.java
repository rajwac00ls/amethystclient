package hack.echo.client.utils.entities;

import hack.echo.client.features.settings.impl.RegistryPickerSetting;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EntityGroups {

    private EntityGroups() {}

    private static RegistryPickerSetting.EntryGroup<EntityType<?>> createTagGroup(CharSequence name, TagKey<EntityType<?>> tag) {
        Set<Identifier> members = new HashSet<>();
        for (var holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag)) {
            holder.unwrapKey().ifPresent(key -> members.add(key.identifier()));
        }
        return new RegistryPickerSetting.EntryGroup<>(
                name,
                type -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(type)
                        .map(key -> members.contains(key.identifier()))
                        .orElse(false)
        );
    }

    private static RegistryPickerSetting.EntryGroup<EntityType<?>> createCategoryGroup(MobCategory category) {
        return new RegistryPickerSetting.EntryGroup<>(
                Concat.of(categoryName(category)),
                type -> type.getCategory() == category
        );
    }

    private static CharSequence categoryName(MobCategory category) {
        return switch (category.getName()) {
            case "monster" -> Concat.of("Hostile");
            case "creature" -> Concat.of("Creatures");
            case "ambient" -> Concat.of("Ambient");
            case "axolotl", "axolotls" -> Concat.of("Axolotls");
            case "underground_water_creature" -> Concat.of("Underground Water");
            case "water_creature" -> Concat.of("Water Creatures");
            case "water_ambient" -> Concat.of("Water Ambient");
            case "player" -> Concat.of("Player");
            default -> Concat.of(category.getName());
        };
    }

    private static List<RegistryPickerSetting.EntryGroup<EntityType<?>>> createGroups() {
        List<RegistryPickerSetting.EntryGroup<EntityType<?>>> groups = new ArrayList<>();
        for (MobCategory category : MobCategory.values()) {
            groups.add(createCategoryGroup(category));
        }
        groups.add(new RegistryPickerSetting.EntryGroup<>(
                Concat.of("Players"),
                type -> type == EntityType.PLAYER
        ));
        groups.add(createTagGroup(Concat.of("Undead"), EntityTypeTags.UNDEAD));
        groups.add(createTagGroup(Concat.of("Arrows"), EntityTypeTags.ARROWS));
        groups.add(createTagGroup(Concat.of("Projectiles"), EntityTypeTags.IMPACT_PROJECTILES));
        return groups;
    }

    private static final class Holder {
        private static final List<RegistryPickerSetting.EntryGroup<EntityType<?>>> ALL_GROUPS = createGroups();
    }

    public static List<RegistryPickerSetting.EntryGroup<EntityType<?>>> getAllGroups() {
        return new ArrayList<>(Holder.ALL_GROUPS);
    }
}
