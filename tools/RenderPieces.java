import org.jetbrains.skia.*;
import org.jetbrains.skia.svg.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Renders the cburnett SVG pieces to the PNGs the app uses (Android cannot draw SVG resources)
 * and the app icon. Run from the project root with skiko on the classpath, see README.
 */
public class RenderPieces {
    static byte[] png(Surface s) { return s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 100).getBytes(); }

    static SVGDOM load(java.nio.file.Path p) throws Exception {
        byte[] b = Files.readAllBytes(p);
        return new SVGDOM(Data.Companion.makeFromBytes(b, 0, b.length));
    }

    public static void main(String[] a) throws Exception {
        java.nio.file.Path in = Paths.get(a[0]), out = Paths.get(a[1]), icons = Paths.get(a[2]);
        Files.createDirectories(out);
        Files.createDirectories(icons);
        // Pieces: 180 px, transparent.
        for (String c : new String[]{"w", "b"}) for (String p : new String[]{"K", "Q", "R", "B", "N", "P"}) {
            SVGDOM svg = load(in.resolve(c + p + ".svg"));
            svg.setContainerSize(45f, 45f); // some pieces (the kings) only have a viewBox
            Surface s = Surface.Companion.makeRasterN32Premul(180, 180);
            s.getCanvas().scale(4f, 4f);
            svg.render(s.getCanvas());
            Files.write(out.resolve(c.toLowerCase() + p.toLowerCase() + ".png"), png(s));
        }
        // Icon: white knight on a dark rounded square (the chalkboard look).
        for (int size : new int[]{1024, 512, 432, 192, 144, 96, 72, 48}) {
            Surface s = Surface.Companion.makeRasterN32Premul(size, size);
            Canvas cv = s.getCanvas();
            float inset = size == 432 ? 0 : size * 0.08f;          // 432 = Android adaptive background, full bleed
            Paint bg = new Paint(); bg.setColor(0xFF1B1B1F);
            if (size == 432) cv.drawRect(Rect.Companion.makeWH(size, size), bg);
            else cv.drawRRect(RRect.Companion.makeLTRB(inset, inset, size - inset, size - inset, size * 0.2f), bg);
            SVGDOM svg = load(in.resolve("wN.svg"));
            float k = size == 432 ? 0.5f : 0.66f;                     // adaptive icons crop to the inner 66 %
            float scale = size * k / 45f;
            cv.translate((size - 45 * scale) / 2, (size - 45 * scale) / 2);
            cv.scale(scale, scale);
            svg.render(cv);
            Files.write(icons.resolve("icon_" + size + ".png"), png(s));
        }
    }
}
