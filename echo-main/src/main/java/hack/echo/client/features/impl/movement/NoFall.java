package hack.echo.client.features.impl.movement;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventMove;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.utils.strings.Concat;

public class NoFall extends Feature {

    public NoFall() {
        super(new FeatureInfo(
                Concat.of("NoFall"),
                Concat.of("Prevents fall damage by spoofing on-ground in movement packets"),
                Category.MOVEMENT
        ));
    }

    private final FloatSetting height = new FloatSetting(Concat.of("Height"), 3.0f, 1.0f, 10.0f, 0.5f);

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (isNull()) return;
        if (mc.player.onGround()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;
        if (mc.player.isPassenger() || mc.player.isFallFlying()) return;
        if (mc.player.getDeltaMovement().y >= 0.0) return;
        if (mc.player.fallDistance <= height.getValue()) return;

        event.setOnGround(true);
    }
}
