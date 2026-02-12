import re, os

BASE = r'c:\Users\Sotirios\Desktop\WorldPainter'

# Read texture files
tex_dir = os.path.join(BASE, 'HytaleAssets', 'Common', 'BlockTextures')
tex_files = set()
for f in os.listdir(tex_dir):
    if os.path.isfile(os.path.join(tex_dir, f)):
        tex_files.add(f.lower())

# Read terrain block IDs
with open(os.path.join(BASE, 'WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrain.java'), 'r') as f:
    content = f.read()

pattern = r'public static final HytaleTerrain (\w+)\s*=\s*new HytaleTerrain\([^,]+,\s*HytaleBlock\.of\("([^"]+)"\)'
matches = re.findall(pattern, content)

def check_candidates(block_id):
    """Check what texture candidates exist for a block ID."""
    found = []
    bases = [block_id]
    
    # Strip Rock_ / Soil_ prefix
    for prefix in ['Rock_', 'Soil_']:
        if block_id.startswith(prefix):
            bases.append(block_id[len(prefix):])
    
    # Poisoned -> Poisonned
    if 'Poisoned' in block_id:
        bases.append(block_id.replace('Poisoned', 'Poisonned'))
    
    for base in bases:
        candidates = [
            base + '_Top.png', base + '.png', base + '_Top_GS.png', base + '_GS.png',
            base + '_Side.png', base + '_Side_GS.png', base + '_Side_Full_GS.png'
        ]
        for c in candidates:
            if c.lower() in tex_files:
                found.append(c)
    
    # Also check _Source -> Fluid_ 
    if block_id.endswith('_Source'):
        fluid = 'Fluid_' + block_id[:-len('_Source')]
        if (fluid + '.png').lower() in tex_files:
            found.append(fluid + '.png')
    
    return found

print("=== BLOCK IDS WITH MATCHING TEXTURES ===")
has_tex = 0
no_tex = 0
no_tex_list = []
for name, block_id in matches:
    found = check_candidates(block_id)
    if found:
        has_tex += 1
        print(f"  {name}: {block_id} -> {found}")
    else:
        no_tex += 1
        no_tex_list.append((name, block_id))

print(f"\n=== HAS TEXTURE: {has_tex}, MISSING: {no_tex} ===")

print("\n=== BLOCKS WITHOUT TEXTURES ===")
for name, block_id in no_tex_list:
    print(f"  {name}: {block_id}")

# Now check what textures COULD match with name variations
print("\n=== POSSIBLE FIXES (name variations) ===")
for name, block_id in no_tex_list:
    # Try different substitutions
    variations = []
    # Gray -> Grey
    if 'Gray' in block_id:
        variations.append(('Gray->Grey', block_id.replace('Gray', 'Grey')))
    # Cyan -> SkyBlue
    if 'Cyan' in block_id:
        variations.append(('Cyan->SkyBlue', block_id.replace('Cyan', 'SkyBlue')))
    # Poisoned -> Poison (without ed)
    if 'Poisoned' in block_id:
        variations.append(('Poisoned->Poison', block_id.replace('Poisoned', 'Poison')))
    # Plant_Moss_Block -> Moss_Block
    if block_id.startswith('Plant_Moss_Block'):
        variations.append(('Plant_Moss_Block->Moss_Block', block_id.replace('Plant_Moss_Block', 'Moss_Block')))
    # Plant_ prefix strip
    if block_id.startswith('Plant_'):
        stripped = block_id[len('Plant_'):]
        variations.append(('strip Plant_', stripped))
    # Neon -> any match?
    
    for desc, var_id in variations:
        found = check_candidates(var_id)
        if found:
            print(f"  {name}: {block_id} => ({desc}) {var_id} -> {found}")

# Also check: which texture files are NOT matched by any terrain?
print("\n=== UNMATCHED TEXTURE FILES (potentially useful) ===")
matched_textures = set()
for name, block_id in matches:
    found = check_candidates(block_id)
    for f in found:
        matched_textures.add(f.lower())

plant_unmatched = [f for f in sorted(tex_files) if f.startswith('plant_') and f not in matched_textures]
moss_unmatched = [f for f in sorted(tex_files) if f.startswith('moss_') and f not in matched_textures]
print("Plant textures not matched:", plant_unmatched)
print("Moss textures not matched:", moss_unmatched)
