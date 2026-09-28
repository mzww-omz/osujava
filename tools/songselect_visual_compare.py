#!/usr/bin/env python3
"""Build a local comparison sheet. No downloads, client assets or pixel-parity claims.

Usage: python3 tools/songselect_visual_compare.py --reference stable.png \
  --capture app-720.png app-1080.png --output /tmp/songselect-comparison.png
Requires Pillow, also used by the skin audit tooling.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--capture', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    paths = [args.reference, *args.capture]
    evidence = []
    for path in paths:
        with Image.open(path) as image:
            evidence.append({'file': str(path.resolve()), 'size': image.size,
                             'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    width, height, caption = 640, 480, 32
    sheet = Image.new('RGB', (width * 2, (height + caption) * len(args.capture)), '#242424')
    draw = ImageDraw.Draw(sheet)
    for index, capture in enumerate(args.capture):
        for column, path in enumerate((args.reference, capture)):
            x, y = column * width, index * (height + caption)
            label = ('STABLE REFERENCE: ' if column == 0 else 'OSUJAVA: ') + path.name
            draw.text((x + 8, y + 8), label, fill='white')
            with Image.open(path) as source:
                image = source.convert('RGB')
                image.thumbnail((width, height), Image.Resampling.LANCZOS)
                sheet.paste(image, (x, y + caption))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)
    args.output.with_suffix('.json').write_text(json.dumps({
        'note': 'Visual inspection aid only; different skin/content is not a pixel parity test.',
        'inputs': evidence}, indent=2) + '\n')


if __name__ == '__main__':
    main()
