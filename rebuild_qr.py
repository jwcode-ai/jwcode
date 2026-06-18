import sys

SQ = chr(39)
DQ = chr(34)

# Read part1 from .tmp
path1 = "C:\\Users\\HUAWEI\\Desktop\\jwcode\\jwcode-web\\src\\components\\Channels\\WechatQrModal.tsx.tmp"
with open(path1, "r", encoding="utf-8") as f:
    part1 = f.readlines()

# Read part2 from .tmp2
path2 = "C:\\Users\\HUAWEI\\Desktop\\jwcode\\jwcode-web\\src\\components\\Channels\\WechatQrModal.tsx.tmp2"
with open(path2, "r", encoding="utf-8-sig") as f:
    part2 = f.readlines()

# Missing code between part1 and part2
missing = [
    '      } catch (e) {\n',
    '        // eslint-disable-next-line no-console\n',
    '        console.warn(' + DQ + '[WechatQr] poll error' + DQ + ', e);\n',
    '      }\n',
]

# Combine
all_lines = part1 + missing + part2

# Apply the phase condition fix
for i, line in enumerate(all_lines):
    stripped = line.strip()
    # Find the line with the first `if (status === ... scaned ... scanned` condition  
    if stripped.startswith('if (status') and SQ + 'scaned' + SQ in stripped and SQ + 'scanned' + SQ in stripped:
        old_if_line = i
        # Verify this is the BUGGY ordering (scaned check first)
        for j in range(i, min(i+6, len(all_lines))):
            if 'else if (status' in all_lines[j] and 'expired' in all_lines[j]:
                old_else_line = j
                old_lines = all_lines[i:j+1]
                
                new_lines = [
                    '        // confirmed ' + chr(26816) + chr(26597) + chr(24517) + chr(20808) + chr(20110) + ' scaned - iLink ' + chr(30830) + chr(35748) + chr(21518) + chr(21518) + ' status ' + chr(20381) + chr(20026) + ' scaned + ret=0\n',
                    '        if (status === ' + SQ + 'confirmed' + SQ + ' || (status === ' + SQ + 'scaned' + SQ + ' && ret === 0)) {\n',
                    '          stopPoll(); setPhase(' + SQ + 'confirmed' + SQ + '); setTimeout(onClose, 1200);\n',
                    '        } else if (status === ' + SQ + 'scaned' + SQ + ' || status === ' + SQ + 'scanned' + SQ + ') {\n',
                    '          setPhase(' + SQ + 'scanned' + SQ + ');\n',
                    '        } else if (status === ' + SQ + 'expired' + SQ + ' || status === ' + SQ + 'canceled' + SQ + ') { stopPoll(); setPhase(' + SQ + 'expired' + SQ + '); }\n',
                ]
                
                all_lines = all_lines[:i] + new_lines + all_lines[j+1:]
                print("Fixed phase condition ordering at line", i+1)
                break
        break

# Write
out_path = "C:\\Users\\HUAWEI\\Desktop\\jwcode\\jwcode-web\\src\\components\\Channels\\WechatQrModal.tsx"
with open(out_path, "w", encoding="utf-8") as f:
    f.writelines(all_lines)

print("Written", len(all_lines), "lines to", out_path)

