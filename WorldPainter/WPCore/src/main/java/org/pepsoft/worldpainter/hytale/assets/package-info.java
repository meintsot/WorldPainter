/**
 * Bridge between WorldPainter and the user's local Hytale installation: locates the
 * installation directory, unpacks the bundled asset zip if needed, and exposes paths
 * to prefab and biome JSON.
 *
 * <p>Classes in this package must not implement {@link java.io.Serializable}.
 * See {@code org.pepsoft.worldpainter.hytale.chunk} package-info for the rationale.
 */
package org.pepsoft.worldpainter.hytale.assets;
