import net.minecraft.client.render.model.CubeFace;
import net.minecraft.client.render.model.json.ModelElementTexture;
import net.minecraft.util.math.Direction;

/**
 * Prints, straight from the real Minecraft classes, which element corner and which UV corner every
 * face vertex gets at rotation 0. Used to derive tools/models/UV_NOTES.md (see there how to run it).
 */
public class UvProbe {
    public static void main(String[] a) {
        // BakedQuadFactory.getPositionMatrix: [WEST]=from.x [DOWN]=from.y [NORTH]=from.z [EAST]=to.x [UP]=to.y [SOUTH]=to.z
        String[] side = new String[6];
        side[CubeFace.DirectionIds.WEST] = "from.x";
        side[CubeFace.DirectionIds.DOWN] = "from.y";
        side[CubeFace.DirectionIds.NORTH] = "from.z";
        side[CubeFace.DirectionIds.EAST] = "to.x";
        side[CubeFace.DirectionIds.UP] = "to.y";
        side[CubeFace.DirectionIds.SOUTH] = "to.z";
        ModelElementTexture t = new ModelElementTexture(new float[]{0f, 0f, 1f, 1f}, 0);
        for (Direction d : Direction.values()) {
            for (int i = 0; i < 4; i++) {
                CubeFace.Corner c = CubeFace.getFace(d).getCorner(i);
                System.out.printf("%s %d %s %s %s u%d v%d%n", d.getName(), i, side[c.xSide], side[c.ySide], side[c.zSide],
                        (int) t.getU(i), (int) t.getV(i));
            }
        }
    }
}
