package hack.echo.client.auth;

import hack.echo.client.Echo;
import net.minecraft.util.Mth;

public class MathProt {

    private static long serverInteger = 0L;
    private static long secretLocalKey = 0L;
    private static long expectedRuntimeConstant = 0L;

    public static void updateVerification(long serverInt, long secretKey, long runtimeConstant) {
        serverInteger = serverInt;
        secretLocalKey = secretKey;
        expectedRuntimeConstant = runtimeConstant;
    }

    private static long computeRuntimeConstant() {
        long xor = serverInteger ^ secretLocalKey;
        return xor + 21332L;
    }

    public static float getEnforcedPitch(float requestedPitch) {
        return Mth.clamp(requestedPitch, -90f, 90f);
    }
}
