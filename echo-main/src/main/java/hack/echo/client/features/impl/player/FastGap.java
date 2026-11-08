package hack.echo.client.features.impl.player;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.impl.render.hud.NotificationsHudModule;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FastGap extends Feature {

	public FastGap() {
		super(new FeatureInfo(
			Concat.of("Fast Gap"),
			Concat.of("Auto release use key after finishing a golden apple"),
			Category.UTILITY
		));
	}

	private final IntSetting releaseTicks = new IntSetting(
		Concat.of("Release Ticks"), 2, 0, 10, Concat.of(" ticks"));
	private final BoolSetting notifyMessage = new BoolSetting(
		Concat.of("Notify"), true);

	private boolean eatingGapple = false;
	private int startCount = -1;
	private InteractionHand eatingHand = InteractionHand.MAIN_HAND;
	private int releaseTicksLeft = 0;

	@Override
	public void onEnable() {
		resetState();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		resetState();
		super.onDisable();
	}

	@EventSubscribe
	private void onTick(EventTick event) {
		if (isNull()) return;
		if (MinecraftCompat.getScreen() != null) return;

		if (releaseTicksLeft > 0) {
			releaseTicksLeft--;
			mc.options.keyUse.setDown(false);
		}

		if (!mc.player.isUsingItem()) {
			if (eatingGapple
				&& startCount != -1
				&& mc.player.getItemInHand(eatingHand).getCount() < startCount) {
				releaseTicksLeft = releaseTicks.getValue();
				mc.options.keyUse.setDown(false);
				if (notifyMessage.getValue()) {
					NotificationsHudModule.notifyCustom(this, Concat.of("Gap Finished"));
				}
			}
			eatingGapple = false;
			startCount = -1;
			return;
		}

		ItemStack usedItem = mc.player.getUseItem();
		if (!usedItem.is(Items.GOLDEN_APPLE)) return;

		if (!eatingGapple) {
			eatingGapple = true;
			eatingHand = mc.player.getUsedItemHand();
			startCount = mc.player.getItemInHand(eatingHand).getCount();
		}
	}

	private void resetState() {
		eatingGapple = false;
		startCount = -1;
		eatingHand = InteractionHand.MAIN_HAND;
		releaseTicksLeft = 0;
	}
}
