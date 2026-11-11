#!/bin/bash
# usage: rawcap.sh <dir-name> <channel> <count>
# Grabs raw frames and prints mean brightness of the video area (rows 170-900) per frame,
# plus a 4x2 grid of mean colors so a different stream ("the ad") stands out. Keeps a PNG every 5th frame.
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb}"
P=com.github.andreyasadchy.xtra.debug
S="${SHOTS_DIR:-./shots}/$1"
mkdir -p "$S"
"$ADB" shell am force-stop $P
"$ADB" logcat -c
"$ADB" shell am start -a android.intent.action.VIEW -d "https://www.twitch.tv/$2" $P >/dev/null
sleep 6
"$ADB" shell input tap 674 1038
for i in $(seq 1 "$3"); do
  t=$(date +%H%M%S)
  "$ADB" exec-out screencap > "$S/$t.raw"
  python -I -c "
import struct,sys
b=open(sys.argv[1],'rb').read(); w,h,f=struct.unpack('<III',b[:12]); off=len(b)-w*h*4
def mean(x0,x1,y0,y1):
    s=n=0; r=g=bl=0
    for y in range(y0,y1,24):
        row=off+y*w*4
        for x in range(x0,x1,24):
            p=row+x*4; r+=b[p]; g+=b[p+1]; bl+=b[p+2]; n+=1
    return (r//n,g//n,bl//n)
grid=[mean(x,x+w//4,y,y+365) for y in (170,535) for x in range(0,w,w//4)][:8]
m=mean(0,w,170,900)
print(sys.argv[2], 'bright=%3d'%(sum(m)//3), ' '.join('%02x%02x%02x'%c for c in grid))
" "$S/$t.raw" "$t"
  if [ $((i % 5)) -eq 0 ]; then "$ADB" shell screencap -p /sdcard/s.png; "$ADB" pull /sdcard/s.png "$S/$t.png" >/dev/null 2>&1; fi
  rm -f "$S/$t.raw"
  sleep 1
done
"$ADB" logcat -d > "$S/logcat.txt"
grep -E "XtraAds|Playback error" "$S/logcat.txt" | head -40
