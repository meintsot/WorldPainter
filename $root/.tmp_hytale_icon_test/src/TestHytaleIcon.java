import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class TestHytaleIcon {
    public static void main(String[] args) throws Exception {
        File assets = new File("c:/Users/Sotirios/Desktop/WorldPainter/HytaleAssets");
        HytaleTerrain.setHytaleAssetsDir(assets);
        BufferedImage img = HytaleTerrain.GRASS.getIcon(null);
        int opaque = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xff) > 0) {
                    opaque++;
                }
            }
        }
        System.out.println("size=" + img.getWidth() + "x" + img.getHeight());
        System.out.println("opaquePixels=" + opaque);
        ImageIO.write(img, "png", new File("$tmp/grass_icon.png"));
        System.out.println("wrote=$tmp/grass_icon.png");
    }
}
