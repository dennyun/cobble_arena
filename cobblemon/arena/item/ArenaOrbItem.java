package cobblemon.arena.item;

import cobblemon.arena.access.ArenaAccessService;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class ArenaOrbItem extends Item {

    public ArenaOrbItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(
        World world,
        PlayerEntity user,
        Hand hand
    ) {
        if (
            !world.isClient() && user instanceof ServerPlayerEntity serverPlayer
        ) {
            ArenaAccessService.openMainGui(serverPlayer);
        }
        return TypedActionResult.success(
            user.getStackInHand(hand),
            world.isClient()
        );
    }

    @Override
    public void appendTooltip(
        ItemStack stack,
        TooltipContext context,
        List<Text> tooltip,
        TooltipType type
    ) {
        tooltip.add(Text.literal("§7Acesse a Arena Competitiva de Cobblemon"));
        tooltip.add(Text.literal("§8Clique com o botão direito para abrir"));
    }
}
