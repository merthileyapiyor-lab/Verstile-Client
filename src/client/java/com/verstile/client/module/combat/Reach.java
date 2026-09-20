package com.verstile.client.module.combat;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Reach — sets ENTITY_INTERACTION_RANGE and BLOCK_INTERACTION_RANGE attributes
 * directly on the local player via transient modifier. No mixin required.
 */
public class Reach extends Module {
    private static final Identifier VERSTILE_REACH_ID = Identifier.fromNamespaceAndPath("verstile", "reach_modifier");

    public final NumberSetting reachDistance = new NumberSetting("Reach Blocks", 3.5, 3.0, 5.0, 0.1);

    public Reach() {
        super("Reach", "Extends block breaking and player reach distance", Category.COMBAT, 0);
        addSetting(reachDistance);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var player = mc.player;

        // Default entity range is 3.0 (survival). We ADD the extra beyond 3.0.
        double extra = reachDistance.getValue() - 3.0;

        var entityAttr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        var blockAttr  = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);

        if (entityAttr != null) {
            entityAttr.removeModifier(VERSTILE_REACH_ID);
            entityAttr.addOrUpdateTransientModifier(
                new AttributeModifier(VERSTILE_REACH_ID, extra, AttributeModifier.Operation.ADD_VALUE)
            );
        }
        if (blockAttr != null) {
            blockAttr.removeModifier(VERSTILE_REACH_ID);
            blockAttr.addOrUpdateTransientModifier(
                new AttributeModifier(VERSTILE_REACH_ID, extra, AttributeModifier.Operation.ADD_VALUE)
            );
        }
    }

    @Override
    public void onDisable() {
        // Clean up modifiers when module is disabled
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var entityAttr = mc.player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        var blockAttr  = mc.player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (entityAttr != null) entityAttr.removeModifier(VERSTILE_REACH_ID);
        if (blockAttr  != null) blockAttr.removeModifier(VERSTILE_REACH_ID);
    }
}
