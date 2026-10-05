#!/usr/bin/env python3
"""APK 서명 인증서 SHA-256 지문(APK Signature Scheme v2/v3 블록에서 직접 읽음, apksigner 없이). 빌드 아님."""
import hashlib, struct, sys

def certs(path):
    d = open(path, 'rb').read()
    eocd = d.rfind(b'PK\x05\x06')
    cd_off = struct.unpack('<I', d[eocd + 16:eocd + 20])[0]
    assert d[cd_off - 16:cd_off] == b'APK Sig Block 42', 'no APK signing block'
    size = struct.unpack('<Q', d[cd_off - 24:cd_off - 16])[0]
    p, end = cd_off - size - 8 + 8, cd_off - 24
    out = []
    while p < end:
        ln, bid = struct.unpack('<QI', d[p:p + 12])
        val = d[p + 12:p + 8 + ln]
        p += 8 + ln
        if bid not in (0x7109871a, 0xf05368c0):  # v2, v3
            continue
        u = lambda b, o: struct.unpack('<I', b[o:o + 4])[0]
        signers = val[4:4 + u(val, 0)]
        signer = signers[4:4 + u(signers, 0)]
        sd = signer[4:4 + u(signer, 0)]
        digs = sd[4:4 + u(sd, 0)]
        cl = sd[4 + len(digs):]
        cl = cl[4:4 + u(cl, 0)]
        cert = cl[4:4 + u(cl, 0)]
        out.append(('v2' if bid == 0x7109871a else 'v3', hashlib.sha256(cert).hexdigest()))
    return out

for f in sys.argv[1:]:
    for scheme, h in certs(f):
        print(f, scheme, h)
