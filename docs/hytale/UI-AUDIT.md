# Hytale UI Audit

This pass updated the main Hytale-specific world editing and export surfaces to avoid showing Minecraft-only wording when a Hytale world is active.

Updated in this pass:
- Export action label and tooltip switch to Hytale wording for Hytale worlds.
- Import action label and tooltip switch to Hytale wording for Hytale worlds.
- File menu import source label switches to `From Hytale world...` for Hytale worlds.
- File menu merge action no longer advertises the Java-only Minecraft merge path on Hytale worlds; it is relabelled for Hytale and disabled because merge-back is not implemented there.
- Dimension properties now show `Hytale settings:` and `Hytale default: 62` when editing Hytale dimensions.
- The resource reset confirmation/tooltip now use Hytale wording when applicable.

Remaining user-visible Minecraft references intentionally left as-is:
- The Hytale block palette maps Minecraft source blocks to Hytale blocks. Those labels are accurate because that tool is explicitly a conversion/mapping UI.
- About dialog references the Minecraft data directory. That dialog is global application metadata, not world-platform-specific UI.
- Import height map dialogs reference Minecraft scale/target terminology that is not specific to an already-loaded Hytale world.

Remaining user-visible Minecraft references not yet platform-specialized:
- Generic Java map-import dialogs still use Minecraft naming in titles and prompts; Hytale uses its own separate import dialog.

Remaining internal-only references not changed:
- Method names, action IDs, comments, and conversion helpers that still mention Minecraft but are not shown directly to the user.