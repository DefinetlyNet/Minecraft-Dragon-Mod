package com.wildcreatures;

import com.wildcreatures.block.BurntSigns;
import com.wildcreatures.block.DragonEggBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, WildCreatures.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<BurntSigns.SignEntity>> SIGN = BLOCK_ENTITIES.register("sign",
            () -> BlockEntityType.Builder.of(BurntSigns.SignEntity::new, ModBlocks.BURNT_OAK_SIGN.get(), ModBlocks.BURNT_OAK_WALL_SIGN.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<BurntSigns.HangingSignEntity>> HANGING_SIGN = BLOCK_ENTITIES.register("hanging_sign",
            () -> BlockEntityType.Builder.of(BurntSigns.HangingSignEntity::new, ModBlocks.BURNT_OAK_HANGING_SIGN.get(), ModBlocks.BURNT_OAK_WALL_HANGING_SIGN.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DragonEggBlockEntity>> DRAGON_EGG = BLOCK_ENTITIES.register("dragon_egg",
            () -> BlockEntityType.Builder.of(DragonEggBlockEntity::new, ModBlocks.RED_DRAGON_EGG.get(), ModBlocks.FROST_DRAGON_EGG.get(),
                    ModBlocks.LIGHTNING_DRAGON_EGG.get(), ModBlocks.BLACK_DRAGON_EGG.get()).build(null));
}
