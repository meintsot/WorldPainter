"""
Tool to compare Hytale region files between a working world and an exported world.
Reads IndexedStorageFile format, decompresses Zstd blobs, parses BSON,
and compares the fluid section data.
"""
import struct
import sys
import os

try:
    import zstandard as zstd
except ImportError:
    print("Installing zstandard...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "zstandard", "pymongo"])
    import zstandard as zstd

import bson

MAGIC_STRING = b"HytaleIndexedStorage"
MAGIC_LENGTH = 20
HEADER_LENGTH = 32

def read_region_header(f):
    """Read region file header."""
    magic = f.read(MAGIC_LENGTH)
    if magic != MAGIC_STRING:
        raise ValueError(f"Invalid magic: {magic}")
    version = struct.unpack(">I", f.read(4))[0]
    blob_count = struct.unpack(">I", f.read(4))[0]
    segment_size = struct.unpack(">I", f.read(4))[0]
    return version, blob_count, segment_size

def read_blob_index(f, blob_count):
    """Read blob index table."""
    indices = []
    for _ in range(blob_count):
        idx = struct.unpack(">I", f.read(4))[0]
        indices.append(idx)
    return indices

def read_chunk(f, blob_index_entry, blob_count, segment_size):
    """Read and decompress a single chunk from the region file."""
    if blob_index_entry == 0:
        return None
    
    segments_base = HEADER_LENGTH + blob_count * 4
    position = segments_base + (blob_index_entry - 1) * segment_size
    
    f.seek(position)
    src_length = struct.unpack(">I", f.read(4))[0]
    compressed_length = struct.unpack(">I", f.read(4))[0]
    compressed_data = f.read(compressed_length)
    
    # Decompress with Zstd
    dctx = zstd.ZstdDecompressor()
    decompressed = dctx.decompress(compressed_data, max_output_size=src_length)
    return decompressed

def parse_bson_chunk(data):
    """Parse BSON chunk data."""
    return bson.decode(data)

def analyze_fluid_section(fluid_doc):
    """Analyze a fluid section BSON document."""
    if "Data" not in fluid_doc:
        return {"status": "no_data"}
    
    data = bytes(fluid_doc["Data"])
    pos = 0
    
    palette_type = data[pos]; pos += 1
    
    result = {"palette_type": palette_type}
    
    PALETTE_NAMES = {0: "EMPTY", 1: "HALF_BYTE", 2: "BYTE", 3: "SHORT"}
    result["palette_type_name"] = PALETTE_NAMES.get(palette_type, "UNKNOWN")
    
    if palette_type == 0:  # EMPTY
        has_levels = bool(data[pos]); pos += 1
        result["has_levels"] = has_levels
        return result
    
    # Read palette entries
    entry_count = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
    result["entry_count"] = entry_count
    
    entries = []
    for _ in range(entry_count):
        internal_id = data[pos]; pos += 1
        str_len = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
        name = data[pos:pos+str_len].decode("utf-8"); pos += str_len
        count = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
        entries.append({"id": internal_id, "name": name, "count": count})
    result["entries"] = entries
    
    # Read block data (nibble-packed for HALF_BYTE)
    if palette_type == 1:  # HALF_BYTE
        block_data = data[pos:pos+16384]; pos += 16384
        
        # Count non-empty fluids
        fluid_blocks = 0
        fluid_positions = []
        for i in range(32768):
            byte_idx = i // 2
            if i % 2 == 0:
                nibble = (block_data[byte_idx] >> 4) & 0xF  # HIGH nibble for even
            else:
                nibble = block_data[byte_idx] & 0xF  # LOW nibble for odd
            if nibble != 0:
                fluid_blocks += 1
                # Decode position: index = y<<10 | z<<5 | x
                x = i & 0x1F
                z = (i >> 5) & 0x1F
                y = (i >> 10) & 0x1F
                if len(fluid_positions) < 20:
                    fluid_positions.append({"index": i, "x": x, "y": y, "z": z, "palette_idx": nibble})
        
        result["fluid_block_count"] = fluid_blocks
        result["sample_positions"] = fluid_positions
    elif palette_type == 2:  # BYTE
        block_data = data[pos:pos+32768]; pos += 32768
        fluid_blocks = sum(1 for b in block_data if b != 0)
        result["fluid_block_count"] = fluid_blocks
    
    # Read level data
    has_levels = bool(data[pos]); pos += 1
    result["has_levels"] = has_levels
    
    if has_levels:
        level_data = data[pos:pos+16384]; pos += 16384
        
        # Count non-zero levels and examine
        non_zero = 0
        level_positions = []
        for i in range(32768):
            byte_idx = i // 2
            if i % 2 == 0:
                level = level_data[byte_idx] & 0xF  # LOW nibble for even
            else:
                level = (level_data[byte_idx] >> 4) & 0xF  # HIGH nibble for odd
            if level != 0:
                non_zero += 1
                x = i & 0x1F
                z = (i >> 5) & 0x1F
                y = (i >> 10) & 0x1F
                if len(level_positions) < 20:
                    level_positions.append({"index": i, "x": x, "y": y, "z": z, "level": level})
        
        result["non_zero_levels"] = non_zero
        result["sample_levels"] = level_positions
    
    return result

def analyze_block_section(block_doc):
    """Analyze a block section BSON document for version and palette info."""
    result = {}
    if "Version" in block_doc:
        result["version"] = block_doc["Version"]
    if "Data" not in block_doc:
        return result
    
    data = bytes(block_doc["Data"])
    pos = 0
    
    # Check if version >= 6: block migration version
    version = block_doc.get("Version", 0)
    if version >= 6:
        migration_ver = struct.unpack(">I", data[pos:pos+4])[0]; pos += 4
        result["migration_version"] = migration_ver
    
    palette_type = data[pos]; pos += 1
    PALETTE_NAMES = {0: "EMPTY", 1: "HALF_BYTE", 2: "BYTE", 3: "SHORT"}
    result["palette_type"] = palette_type
    result["palette_type_name"] = PALETTE_NAMES.get(palette_type, "UNKNOWN")
    
    if palette_type == 0:
        return result
    
    entry_count = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
    result["entry_count"] = entry_count
    
    entries = []
    for _ in range(entry_count):
        internal_id = data[pos]; pos += 1
        str_len = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
        name = data[pos:pos+str_len].decode("utf-8"); pos += str_len
        count = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
        entries.append({"id": internal_id, "name": name, "count": count})
    result["entries"] = entries
    
    return result

def analyze_heightmap(block_chunk_doc):
    """Analyze heightmap from BlockChunk Data."""
    if "Data" not in block_chunk_doc:
        return {"status": "no_data"}
    
    data = bytes(block_chunk_doc["Data"])
    pos = 0
    result = {}
    
    if "Version" in block_chunk_doc:
        result["version"] = block_chunk_doc["Version"]
    
    # needsPhysics: boolean
    needs_physics = bool(data[pos]); pos += 1
    result["needs_physics"] = needs_physics
    
    # ShortBytePalette for heightmap (little-endian)
    palette_count = struct.unpack("<H", data[pos:pos+2])[0]; pos += 2
    heights_palette = []
    for _ in range(palette_count):
        h = struct.unpack("<h", data[pos:pos+2])[0]; pos += 2
        heights_palette.append(h)
    result["height_palette_count"] = palette_count
    result["height_palette_values"] = heights_palette[:20]  # First 20
    result["height_min"] = min(heights_palette) if heights_palette else None
    result["height_max"] = max(heights_palette) if heights_palette else None
    
    # BitFieldArr for heightmap indices
    bitfield_length = struct.unpack("<I", data[pos:pos+4])[0]; pos += 4
    result["height_bitfield_length"] = bitfield_length
    
    # Decode some heightmap values
    bitfield_data = data[pos:pos+bitfield_length]; pos += bitfield_length
    
    # Read first few heightmap entries (10 bits per entry)
    sample_heights = []
    for col_idx in range(min(64, 1024)):
        bit_index = col_idx * 10
        value = 0
        for bit in range(10):
            byte_idx = (bit_index + bit) // 8
            bit_offset = (bit_index + bit) % 8
            if byte_idx < len(bitfield_data):
                value |= ((bitfield_data[byte_idx] >> bit_offset) & 1) << bit
        x = col_idx & 0x1F
        z = col_idx >> 5
        actual_height = heights_palette[value] if value < len(heights_palette) else -1
        sample_heights.append({"x": x, "z": z, "palette_idx": value, "height": actual_height})
    
    result["sample_heights"] = sample_heights[:20]
    
    return result

def analyze_region(filepath, label):
    """Analyze a region file and print summary."""
    print(f"\n{'='*80}")
    print(f"ANALYZING: {label}")
    print(f"File: {filepath}")
    print(f"{'='*80}")
    
    if not os.path.exists(filepath):
        print(f"  FILE NOT FOUND!")
        return
    
    with open(filepath, "rb") as f:
        version, blob_count, segment_size = read_region_header(f)
        print(f"  Version: {version}, Blobs: {blob_count}, Segment Size: {segment_size}")
        
        blob_indices = read_blob_index(f, blob_count)
        non_empty = sum(1 for idx in blob_indices if idx != 0)
        print(f"  Non-empty chunks: {non_empty}/{blob_count}")
        
        # Find first few non-empty chunks
        chunks_analyzed = 0
        for blob_idx, first_segment in enumerate(blob_indices):
            if first_segment == 0:
                continue
            if chunks_analyzed >= 3:  # Only analyze first 3 chunks
                break
            
            local_x = blob_idx % 32
            local_z = blob_idx // 32
            
            print(f"\n  --- Chunk local ({local_x}, {local_z}) ---")
            
            try:
                raw_data = read_chunk(f, first_segment, blob_count, segment_size)
                if raw_data is None:
                    print(f"    Empty chunk")
                    continue
                
                print(f"    Raw data size: {len(raw_data)} bytes")
                
                chunk_doc = parse_bson_chunk(raw_data)
                
                # Print top-level keys
                top_keys = list(chunk_doc.keys())
                print(f"    Top-level keys: {top_keys}")
                
                if "Components" in chunk_doc:
                    comp_keys = list(chunk_doc["Components"].keys())
                    print(f"    Component keys: {comp_keys}")
                    
                    # Analyze BlockChunk (heightmap)
                    if "BlockChunk" in chunk_doc["Components"]:
                        bc = chunk_doc["Components"]["BlockChunk"]
                        hm = analyze_heightmap(bc)
                        print(f"\n    [BlockChunk]")
                        print(f"      Version: {hm.get('version', 'N/A')}")
                        print(f"      Needs Physics: {hm.get('needs_physics', 'N/A')}")
                        print(f"      Height palette count: {hm.get('height_palette_count', 'N/A')}")
                        print(f"      Height range: {hm.get('height_min')} - {hm.get('height_max')}")
                        print(f"      Sample heights: {hm.get('sample_heights', [])[:5]}")
                    
                    # Analyze ChunkColumn sections
                    if "ChunkColumn" in chunk_doc["Components"]:
                        cc = chunk_doc["Components"]["ChunkColumn"]
                        if "Sections" in cc:
                            sections = cc["Sections"]
                            print(f"\n    [ChunkColumn] {len(sections)} sections")
                            
                            for sec_idx, sec in enumerate(sections):
                                sec_comps = sec.get("Components", {})
                                
                                # Block section
                                if "Block" in sec_comps:
                                    bs = analyze_block_section(sec_comps["Block"])
                                    block_info = f"palette={bs.get('palette_type_name', '?')}"
                                    if "entries" in bs:
                                        entry_names = [e["name"] for e in bs["entries"][:5]]
                                        block_info += f" blocks={entry_names}"
                                    print(f"      Section {sec_idx} (Y {sec_idx*32}-{sec_idx*32+31}): Block({block_info})")
                                
                                # Fluid section
                                if "Fluid" in sec_comps:
                                    fs = analyze_fluid_section(sec_comps["Fluid"])
                                    fluid_info = f"palette={fs.get('palette_type_name', '?')}"
                                    if "entries" in fs:
                                        entry_names = [e["name"] for e in fs["entries"]]
                                        fluid_info += f" fluids={entry_names}"
                                    if "fluid_block_count" in fs:
                                        fluid_info += f" count={fs['fluid_block_count']}"
                                    if "non_zero_levels" in fs:
                                        fluid_info += f" levels={fs['non_zero_levels']}"
                                    if fs.get("sample_positions"):
                                        fluid_info += f"\n        Fluid positions: {fs['sample_positions'][:10]}"
                                    if fs.get("sample_levels"):
                                        fluid_info += f"\n        Level positions: {fs['sample_levels'][:10]}"
                                    print(f"      Section {sec_idx} (Y {sec_idx*32}-{sec_idx*32+31}): Fluid({fluid_info})")
                                
                                # Other components
                                other_comps = [k for k in sec_comps.keys() if k not in ("Block", "Fluid", "ChunkSection")]
                                if other_comps:
                                    print(f"      Section {sec_idx}: Other components: {other_comps}")
                    
                    # Entity chunk
                    if "EntityChunk" in chunk_doc["Components"]:
                        ec = chunk_doc["Components"]["EntityChunk"]
                        if "Entities" in ec:
                            print(f"\n    [EntityChunk] {len(ec['Entities'])} entities")
                
            except Exception as e:
                print(f"    ERROR: {e}")
                import traceback
                traceback.print_exc()
            
            chunks_analyzed += 1

if __name__ == "__main__":
    # Working world (Hytale-generated)
    working_dir = r"C:\Users\Sotirios\Desktop\WorldPainter\universe\worlds\default\chunks"
    
    # Exported world (WorldPainter-generated) 
    exported_dir = r"C:\Users\Sotirios\Desktop\KOC\run\universe\worlds\Generated World\chunks"
    exported_dir2 = r"C:\Users\Sotirios\Desktop\KOC\run\universe\worlds\default\chunks"
    
    # Find and analyze region files
    for label, dir_path in [
        ("Working World (universe/default)", working_dir),
        ("Exported World (Generated World)", exported_dir),
        ("Exported World (default)", exported_dir2),
    ]:
        if os.path.isdir(dir_path):
            region_files = [f for f in os.listdir(dir_path) if f.endswith(".region.bin")]
            if region_files:
                # Analyze the 0.0 region or first available
                target = "0.0.region.bin" if "0.0.region.bin" in region_files else region_files[0]
                analyze_region(os.path.join(dir_path, target), f"{label} - {target}")
            else:
                print(f"\nNo region files found in {dir_path}")
        else:
            print(f"\nDirectory not found: {dir_path}")
