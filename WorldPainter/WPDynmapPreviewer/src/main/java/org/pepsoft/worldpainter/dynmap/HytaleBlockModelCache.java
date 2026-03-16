package org.pepsoft.worldpainter.dynmap;

import com.google.gson.*;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses and caches Hytale {@code .blockymodel} files for 3D rendering in the
 * isometric preview. Each model is a collection of oriented quads and/or boxes
 * with an associated texture.
 *
 * <p>Coordinates in blockymodel files use Hytale's 32-units-per-block system.
 * This class normalises everything to block-local coordinates (0..1).
 */
class HytaleBlockModelCache {

    /**
     * Get the parsed model for a block ID, or {@code null} if the block has no
     * custom model or the model could not be loaded.
     */
    static ParsedModel getModel(String blockId) {
        return MODEL_CACHE.computeIfAbsent(blockId, HytaleBlockModelCache::loadModel);
    }

    // ── Model loading ───────────────────────────────────────────────

    private static ParsedModel loadModel(String blockId) {
        String[] paths = HytaleTerrain.getModelPathsForBlockId(blockId);
        if (paths == null) {
            return EMPTY;
        }
        String modelPath = paths[0];
        String texturePath = paths[1];

        File assetsDir = HytaleTerrain.getHytaleAssetsDir();
        if (assetsDir == null) {
            return EMPTY;
        }

        // Load model JSON
        File modelFile = resolveAsset(assetsDir, modelPath);
        if (modelFile == null) {
            return EMPTY;
        }
        List<ModelQuad> quads;
        try {
            quads = parseBlockyModel(modelFile);
        } catch (Exception e) {
            logger.debug("Failed to parse blockymodel {}", modelFile, e);
            return EMPTY;
        }
        if (quads.isEmpty()) {
            return EMPTY;
        }

        // Load texture
        BufferedImage texture = null;
        int avgColour = 0xFFA0A0A0;
        File textureFile = resolveAsset(assetsDir, texturePath);
        if (textureFile != null) {
            try {
                texture = ImageIO.read(textureFile);
                if (texture != null) {
                    avgColour = computeAverageColour(texture);
                }
            } catch (IOException e) {
                logger.debug("Failed to load model texture {}", textureFile, e);
            }
        }

        return new ParsedModel(quads, texture, avgColour);
    }

    private static List<ModelQuad> parseBlockyModel(File modelFile) throws IOException {
        List<ModelQuad> quads = new ArrayList<>();
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(modelFile), StandardCharsets.UTF_8))) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray nodes = root.getAsJsonArray("nodes");
            if (nodes == null) {
                return quads;
            }
            parseNodes(nodes, quads);
        }
        return quads;
    }

    /**
     * Recursively parse nodes and their children.
     */
    private static void parseNodes(JsonArray nodes, List<ModelQuad> quads) {
        for (JsonElement nodeElem : nodes) {
            JsonObject node = nodeElem.getAsJsonObject();
            JsonObject shape = node.getAsJsonObject("shape");
            if (shape != null) {
                String type = shape.has("type") ? shape.get("type").getAsString() : "none";
                if (!shape.has("visible") || shape.get("visible").getAsBoolean()) {
                    if ("quad".equals(type)) {
                        parseQuad(node, quads);
                    } else if ("box".equals(type)) {
                        parseBox(node, quads);
                    }
                }
            }
            // Recurse into children
            JsonArray children = node.getAsJsonArray("children");
            if (children != null && children.size() > 0) {
                parseNodes(children, quads);
            }
        }
    }

    /**
     * Parse a quad node: a single flat plane defined by position, quaternion
     * orientation, size and stretch.
     */
    private static void parseQuad(JsonObject node, List<ModelQuad> out) {
        JsonObject pos = node.getAsJsonObject("position");
        JsonObject ori = node.getAsJsonObject("orientation");
        JsonObject shape = node.getAsJsonObject("shape");
        JsonObject settings = shape.getAsJsonObject("settings");
        JsonObject sizeObj = settings.getAsJsonObject("size");

        double px = pos.get("x").getAsDouble();
        double py = pos.get("y").getAsDouble();
        double pz = pos.get("z").getAsDouble();
        // Add shape offset
        JsonObject shapeOffset = shape.getAsJsonObject("offset");
        if (shapeOffset != null) {
            px += shapeOffset.get("x").getAsDouble();
            py += shapeOffset.get("y").getAsDouble();
            pz += shapeOffset.get("z").getAsDouble();
        }

        double qx = ori.get("x").getAsDouble();
        double qy = ori.get("y").getAsDouble();
        double qz = ori.get("z").getAsDouble();
        double qw = ori.get("w").getAsDouble();

        double sizeW = sizeObj.get("x").getAsDouble();
        double sizeH = sizeObj.get("y").getAsDouble();
        double halfW = sizeW / 2.0;
        double halfH = sizeH / 2.0;

        // Apply stretch
        JsonObject stretch = shape.getAsJsonObject("stretch");
        double stretchX = 1, stretchY = 1;
        if (stretch != null) {
            stretchX = stretch.get("x").getAsDouble();
            stretchY = stretch.get("y").getAsDouble();
        }
        halfW *= stretchX;
        halfH *= stretchY;

        // Parse texture layout for UV mapping
        int texOffsetX = 0, texOffsetY = 0;
        boolean mirrorX = false, mirrorY = false;
        int texAngle = 0;
        JsonObject textureLayout = shape.getAsJsonObject("textureLayout");
        if (textureLayout != null) {
            JsonObject front = textureLayout.getAsJsonObject("front");
            if (front != null) {
                JsonObject offset = front.getAsJsonObject("offset");
                if (offset != null) {
                    texOffsetX = offset.get("x").getAsInt();
                    texOffsetY = offset.get("y").getAsInt();
                }
                JsonObject mirror = front.getAsJsonObject("mirror");
                if (mirror != null) {
                    mirrorX = mirror.get("x").getAsBoolean();
                    mirrorY = mirror.get("y").getAsBoolean();
                }
                if (front.has("angle")) {
                    texAngle = front.get("angle").getAsInt();
                }
            }
        }

        // Base quad vertices (normal = +Z, centered at origin)
        // v0=bottom-left, v1=bottom-right, v2=top-right, v3=top-left
        double[][] base = {
            {-halfW, -halfH, 0},
            { halfW, -halfH, 0},
            { halfW,  halfH, 0},
            {-halfW,  halfH, 0}
        };

        // Rotate each vertex by quaternion and translate
        double[][] verts = new double[4][3];
        for (int i = 0; i < 4; i++) {
            double[] rotated = rotateByQuaternion(base[i], qx, qy, qz, qw);
            // Convert from Hytale units (32 per block) to block-local coords (0..1).
            // Model origin is at block center-bottom (16, 0, 16) in Hytale units:
            // X and Z are centered (+16), Y starts at block bottom (no offset).
            verts[i][0] = (rotated[0] + px + 16) / 32.0;
            verts[i][1] = (rotated[1] + py) / 32.0;
            verts[i][2] = (rotated[2] + pz + 16) / 32.0;
        }

        boolean doubleSided = shape.has("doubleSided") && shape.get("doubleSided").getAsBoolean();
        out.add(new ModelQuad(verts, doubleSided, (int) sizeW, (int) sizeH,
                texOffsetX, texOffsetY, mirrorX, mirrorY, texAngle));
    }

    /**
     * Parse a box node into 6 quads (the 6 faces of the cuboid).
     */
    private static void parseBox(JsonObject node, List<ModelQuad> out) {
        JsonObject pos = node.getAsJsonObject("position");
        JsonObject ori = node.getAsJsonObject("orientation");
        JsonObject shape = node.getAsJsonObject("shape");
        JsonObject settings = shape.getAsJsonObject("settings");
        JsonObject sizeObj = settings.getAsJsonObject("size");

        double px = pos.get("x").getAsDouble();
        double py = pos.get("y").getAsDouble();
        double pz = pos.get("z").getAsDouble();
        JsonObject shapeOffset = shape.getAsJsonObject("offset");
        if (shapeOffset != null) {
            px += shapeOffset.get("x").getAsDouble();
            py += shapeOffset.get("y").getAsDouble();
            pz += shapeOffset.get("z").getAsDouble();
        }

        double qx = ori.get("x").getAsDouble();
        double qy = ori.get("y").getAsDouble();
        double qz = ori.get("z").getAsDouble();
        double qw = ori.get("w").getAsDouble();

        double hw = sizeObj.get("x").getAsDouble() / 2.0;
        double hh = sizeObj.get("y").getAsDouble() / 2.0;
        double hd = sizeObj.get("z").getAsDouble() / 2.0;

        JsonObject stretch = shape.getAsJsonObject("stretch");
        if (stretch != null) {
            hw *= stretch.get("x").getAsDouble();
            hh *= stretch.get("y").getAsDouble();
            hd *= stretch.get("z").getAsDouble();
        }

        boolean doubleSided = shape.has("doubleSided") && shape.get("doubleSided").getAsBoolean();

        // 8 corners of the box
        double[][] corners = {
            {-hw, -hh, -hd}, { hw, -hh, -hd}, { hw,  hh, -hd}, {-hw,  hh, -hd}, // back face
            {-hw, -hh,  hd}, { hw, -hh,  hd}, { hw,  hh,  hd}, {-hw,  hh,  hd}  // front face
        };

        // Rotate and translate all corners
        double[][] c = new double[8][3];
        for (int i = 0; i < 8; i++) {
            double[] rotated = rotateByQuaternion(corners[i], qx, qy, qz, qw);
            c[i][0] = (rotated[0] + px + 16) / 32.0;
            c[i][1] = (rotated[1] + py) / 32.0;
            c[i][2] = (rotated[2] + pz + 16) / 32.0;
        }

        // 6 faces of the box (CCW winding) — use default texture mapping
        out.add(new ModelQuad(new double[][]{c[0], c[1], c[2], c[3]}, doubleSided));
        out.add(new ModelQuad(new double[][]{c[5], c[4], c[7], c[6]}, doubleSided));
        out.add(new ModelQuad(new double[][]{c[4], c[0], c[3], c[7]}, doubleSided));
        out.add(new ModelQuad(new double[][]{c[1], c[5], c[6], c[2]}, doubleSided));
        out.add(new ModelQuad(new double[][]{c[3], c[2], c[6], c[7]}, doubleSided));
        out.add(new ModelQuad(new double[][]{c[4], c[5], c[1], c[0]}, doubleSided));
    }

    // ── Quaternion rotation ──────────────────────────────────────────

    private static double[] rotateByQuaternion(double[] v, double qx, double qy, double qz, double qw) {
        double tx = 2 * (qy * v[2] - qz * v[1]);
        double ty = 2 * (qz * v[0] - qx * v[2]);
        double tz = 2 * (qx * v[1] - qy * v[0]);
        return new double[] {
            v[0] + qw * tx + (qy * tz - qz * ty),
            v[1] + qw * ty + (qz * tx - qx * tz),
            v[2] + qw * tz + (qx * ty - qy * tx)
        };
    }

    // ── Average colour computation ───────────────────────────────────

    private static int computeAverageColour(BufferedImage texture) {
        long totalR = 0, totalG = 0, totalB = 0;
        int count = 0;
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int argb = texture.getRGB(x, y);
                if (((argb >> 24) & 0xFF) > 128) {
                    totalR += (argb >> 16) & 0xFF;
                    totalG += (argb >> 8) & 0xFF;
                    totalB += argb & 0xFF;
                    count++;
                }
            }
        }
        if (count == 0) {
            return 0xFFA0A0A0;
        }
        int r = (int) (totalR / count);
        int g = (int) (totalG / count);
        int b = (int) (totalB / count);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ── Asset file resolution ────────────────────────────────────────

    private static File resolveAsset(File assetsDir, String assetPath) {
        if (assetPath == null || assetPath.isEmpty()) {
            return null;
        }
        String normalised = assetPath.replace('/', File.separatorChar);
        File f = new File(assetsDir, "Common" + File.separator + normalised);
        if (f.isFile()) {
            return f;
        }
        f = new File(assetsDir, normalised);
        return f.isFile() ? f : null;
    }

    // ── Data classes ─────────────────────────────────────────────────

    /**
     * A single quad in the model, defined by 4 vertices in block-local
     * coordinates (0..1).
     */
    static final class ModelQuad {
        final double[][] v;
        final boolean doubleSided;
        final double[] edge1, edge2;
        // Texture mapping: quad size in pixels and texture atlas offset
        final int texSizeW, texSizeH;
        final int texOffsetX, texOffsetY;
        final boolean mirrorX, mirrorY;
        final int texAngle;

        ModelQuad(double[][] vertices, boolean doubleSided,
                  int texSizeW, int texSizeH,
                  int texOffsetX, int texOffsetY,
                  boolean mirrorX, boolean mirrorY, int texAngle) {
            this.v = vertices;
            this.doubleSided = doubleSided;
            this.texSizeW = texSizeW;
            this.texSizeH = texSizeH;
            this.texOffsetX = texOffsetX;
            this.texOffsetY = texOffsetY;
            this.mirrorX = mirrorX;
            this.mirrorY = mirrorY;
            this.texAngle = texAngle;
            edge1 = new double[] {v[1][0]-v[0][0], v[1][1]-v[0][1], v[1][2]-v[0][2]};
            edge2 = new double[] {v[3][0]-v[0][0], v[3][1]-v[0][1], v[3][2]-v[0][2]};
        }

        /** Convenience constructor for box faces (no texture layout info). */
        ModelQuad(double[][] vertices, boolean doubleSided) {
            this(vertices, doubleSided, 32, 32, 0, 0, false, false, 0);
        }

        /**
         * Ray-quad intersection. Returns a 3-element array [t, u, v] where
         * u,v are parametric coordinates on the quad (0..1), or null if no hit.
         */
        double[] intersect(double ox, double oy, double oz,
                           double dx, double dy, double dz) {
            double[] dir = {dx, dy, dz};
            double[] h = cross(dir, edge2);
            double a = dot(edge1, h);
            if (!doubleSided && a < 0) {
                return null;
            }
            if (Math.abs(a) < 1e-10) {
                return null;
            }
            double f = 1.0 / a;
            double[] s = {ox - v[0][0], oy - v[0][1], oz - v[0][2]};
            double u = f * dot(s, h);
            if (u < -0.001 || u > 1.001) {
                return null;
            }
            double[] q = cross(s, edge1);
            double vp = f * dot(dir, q);
            if (vp < -0.001 || u + vp > 1.002) {
                // Try second triangle of the quad (v2, v3, v1)
                return intersectSecondTriangle(ox, oy, oz, dx, dy, dz);
            }
            double t = f * dot(edge2, q);
            if (t <= 1e-6) {
                return null;
            }
            // Clamp UV to 0..1
            return new double[] {t, Math.max(0, Math.min(1, u)), Math.max(0, Math.min(1, vp))};
        }

        private double[] intersectSecondTriangle(double ox, double oy, double oz,
                                                  double dx, double dy, double dz) {
            double[] e1 = {v[3][0]-v[2][0], v[3][1]-v[2][1], v[3][2]-v[2][2]};
            double[] e2 = {v[1][0]-v[2][0], v[1][1]-v[2][1], v[1][2]-v[2][2]};
            double[] dir = {dx, dy, dz};
            double[] h = cross(dir, e2);
            double a = dot(e1, h);
            if (Math.abs(a) < 1e-10) {
                return null;
            }
            double f = 1.0 / a;
            double[] s = {ox - v[2][0], oy - v[2][1], oz - v[2][2]};
            double u = f * dot(s, h);
            if (u < -0.001 || u > 1.001) {
                return null;
            }
            double[] q = cross(s, e1);
            double vp = f * dot(dir, q);
            if (vp < -0.001 || u + vp > 1.002) {
                return null;
            }
            double t = f * dot(e2, q);
            if (t <= 1e-6) {
                return null;
            }
            // For the second triangle (v2->v3, v2->v1), convert to quad UV:
            // u along v2->v3 corresponds to quad V from 1 to 0
            // vp along v2->v1 corresponds to quad U from 1 to 0
            double quadU = Math.max(0, Math.min(1, 1.0 - vp));
            double quadV = Math.max(0, Math.min(1, 1.0 - u));
            return new double[] {t, quadU, quadV};
        }

        /**
         * Sample the texture at the given quad UV coordinates.
         * Returns an ARGB pixel value, or 0 if the pixel is transparent.
         */
        int sampleTexture(BufferedImage texture, double u, double v) {
            if (texture == null) {
                return 0; // No texture — transparent
            }

            // Apply mirror
            if (mirrorX) { u = 1.0 - u; }
            if (mirrorY) { v = 1.0 - v; }

            // Apply texture rotation (angle is in degrees: 0, 90, 180, 270)
            double ru = u, rv = v;
            switch (texAngle) {
                case 90:  ru = v;       rv = 1.0 - u; break;
                case 180: ru = 1.0 - u; rv = 1.0 - v; break;
                case 270: ru = 1.0 - v; rv = u;       break;
            }

            int texW = texture.getWidth();
            int texH = texture.getHeight();

            // Map UV to pixel coordinates with wrapping (modular arithmetic)
            // The offset is the texture-space pixel where the quad's (0,0) maps to
            int px = (texOffsetX + (int) (ru * texSizeW)) % texW;
            int py = (texOffsetY + (int) (rv * texSizeH)) % texH;
            if (px < 0) { px += texW; }
            if (py < 0) { py += texH; }

            int argb = texture.getRGB(px, py);
            int alpha = (argb >> 24) & 0xFF;
            if (alpha < 128) {
                return 0; // Transparent pixel — ray passes through
            }
            return argb | 0xFF000000; // Ensure full alpha
        }

        private static double[] cross(double[] a, double[] b) {
            return new double[] {
                a[1]*b[2] - a[2]*b[1],
                a[2]*b[0] - a[0]*b[2],
                a[0]*b[1] - a[1]*b[0]
            };
        }

        private static double dot(double[] a, double[] b) {
            return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
        }
    }

    /**
     * A parsed block model with its quads, texture, and average texture colour.
     */
    static final class ParsedModel {
        final List<ModelQuad> quads;
        /** The model texture image for per-pixel sampling, or null if not loaded. */
        final BufferedImage texture;
        /** Average colour of non-transparent pixels in the model texture (ARGB). */
        final int colour;

        ParsedModel(List<ModelQuad> quads, BufferedImage texture, int colour) {
            this.quads = quads;
            this.texture = texture;
            this.colour = colour;
        }

        boolean isEmpty() {
            return quads == null || quads.isEmpty();
        }
    }

    private static final ParsedModel EMPTY = new ParsedModel(null, null, 0);
    private static final Map<String, ParsedModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(HytaleBlockModelCache.class);
}
