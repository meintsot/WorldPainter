import struct, os
import zstandard as zstd
import bson

HEADER_LENGTH = 32

path = r'C:\Users\Sotirios\Desktop\KOC\run\universe\worlds\default\chunks\0.0.region.bin'
print(f"Reading: {path}")
print(f"File size: {os.path.getsize(path)} bytes")

with open(path, 'rb') as f:
    magic = f.read(20)
    version = struct.unpack('>I', f.read(4))[0]
    blob_count = struct.unpack('>I', f.read(4))[0]
    segment_size = struct.unpack('>I', f.read(4))[0]
    indices = [struct.unpack('>I', f.read(4))[0] for _ in range(blob_count)]
    
    chunks_with_water = 0
    for bi, fs in enumerate(indices):
        if fs == 0: continue
        segments_base = HEADER_LENGTH + blob_count * 4
        pos = segments_base + (fs - 1) * segment_size
        f.seek(pos)
        src_len = struct.unpack('>I', f.read(4))[0]
        comp_len = struct.unpack('>I', f.read(4))[0]
        comp_data = f.read(comp_len)
        dctx = zstd.ZstdDecompressor()
        raw = dctx.decompress(comp_data, max_output_size=src_len)
        doc = bson.decode(raw)
        
        local_x = bi % 32
        local_z = bi // 32
        print(f"\nChunk ({local_x},{local_z}):")
        
        cc = doc['Components'].get('ChunkColumn', {})
        if 'Sections' not in cc:
            print("  No ChunkColumn/Sections")
            break
        
        for si, sec in enumerate(cc['Sections']):
            comps = sec.get('Components', {})
            if 'Fluid' not in comps: continue
            fd = bytes(comps['Fluid'].get('Data', b''))
            if not fd: continue
            pt = fd[0]
            if pt == 0: continue
            
            p = 1
            ec = struct.unpack('>H', fd[p:p+2])[0]; p += 2
            entries = []
            for _ in range(ec):
                iid = fd[p]; p += 1
                sl = struct.unpack('>H', fd[p:p+2])[0]; p += 2
                nm = fd[p:p+sl].decode(); p += sl
                cnt = struct.unpack('>H', fd[p:p+2])[0]; p += 2
                entries.append((iid, nm, cnt))
            print(f'  Section {si} (Y {si*32}-{si*32+31}): palette={entries}')
            
            p += 16384  # skip block data
            has_levels = bool(fd[p]); p += 1
            print(f'    has_levels={has_levels}')
            if has_levels:
                ld = fd[p:p+16384]
                levels_found = []
                for i in range(32768):
                    bi2 = i >> 1
                    if (i & 1) == 0:
                        lv = ld[bi2] & 0xF
                    else:
                        lv = (ld[bi2] >> 4) & 0xF
                    if lv != 0 and len(levels_found) < 20:
                        x = i & 0x1F
                        z = (i >> 5) & 0x1F
                        y = (i >> 10) & 0x1F
                        levels_found.append(f'({x},{y},{z})=level{lv}')
                print(f'    Levels: {levels_found}')
        break  # Just first 5 chunks with water
    chunks_with_water += 1
    if chunks_with_water >= 5:
