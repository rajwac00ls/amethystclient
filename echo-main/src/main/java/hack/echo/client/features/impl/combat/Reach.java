package hack.echo.client.features.impl.combat;

import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.utils.strings.Concat;

public class Reach extends Feature {

    public Reach() {
        super(new FeatureInfo(
                Concat.of("Reach"),
                Concat.of("Extends the range you can attack and interact with blocks"),
                Category.DETECTED
        ));
    }

    private final FloatSetting entityRange = new FloatSetting(Concat.of("Reach"), 4.0f, 3.0f, 6.0f, 0.1f);
    private final FloatSetting blockRange = new FloatSetting(Concat.of("Block Reach"), 5.0f, 4.5f, 6.0f, 0.1f);

    public double getEntityRange() {
        return entityRange.getValue();
    }

    public double getBlockRange() {
        return blockRange.getValue();
    }
}
