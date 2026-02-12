import re
from collections import Counter

colors = []
with open(r'c:\Users\Sotirios\Desktop\WorldPainter\WorldPainter\WPCore\src\main\java\org\pepsoft\worldpainter\hytale\HytaleTerrain.java', 'r') as f:
    pending = None
    for line in f:
        m = re.search(r'public static final HytaleTerrain (\w+)\s*=', line)
        if m:
            pending = m.group(1)
        cm = re.search(r'(0x[0-9a-fA-F]{6})\)', line)
        if pending and cm:
            colors.append((pending, cm.group(1)))
            pending = None

color_values = [c[1].lower() for c in colors]
unique = len(set(color_values))
print(f'Total: {len(colors)}, Unique: {unique}, Duplicates: {len(colors) - unique}')

counts = Counter(color_values)
dups = {c: n for c, n in counts.items() if n > 1}
if dups:
    for c, n in sorted(dups.items()):
        names = [name for name, val in colors if val.lower() == c]
        print(f'  {c} ({n}x): {names}')
else:
    print('No duplicates!')

# Show some samples for visual check
print('\n--- Sample colors by category ---')
categories = {}
current_cat = 'unknown'
for name, color in colors:
    if 'LEAVES' in name:
        current_cat = 'LEAVES'
    elif 'GRASS' in name:
        current_cat = 'GRASS'
    elif 'CORAL' in name:
        current_cat = 'CORAL'
    elif 'MOSS' in name:
        current_cat = 'MOSS'
    elif 'MUSHROOM' in name or 'BOOMSHROOM' in name:
        current_cat = 'MUSHROOM'
    else:
        current_cat = 'OTHER'
    categories.setdefault(current_cat, []).append((name, color))

for cat, entries in categories.items():
    print(f'\n{cat} ({len(entries)} terrains):')
    for name, color in entries[:8]:
        print(f'  {name}: {color}')
    if len(entries) > 8:
        print(f'  ... and {len(entries) - 8} more')
