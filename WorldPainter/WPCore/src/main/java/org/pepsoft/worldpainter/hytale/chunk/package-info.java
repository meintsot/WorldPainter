/**
 * In-memory chunk representation and on-disk persistence for the Hytale platform.
 *
 * <p>Classes in this package must not implement {@link java.io.Serializable}: world
 * files are persisted via Java standard serialization, and the class FQN is baked into
 * the serialized stream. Serializable types belong directly under
 * {@code org.pepsoft.worldpainter.hytale} so moving them would not break existing saves.
 */
package org.pepsoft.worldpainter.hytale.chunk;
