"""Synthetic audit corpus; python3 -B -m unittest discover -s tools -p 'test_*.py'."""
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw
from songselect_skin_audit import scan, difference, diffs

class AuditTest(unittest.TestCase):
    def test_unusual_dimensions_are_diagnostics_and_transparency_is_presence(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp);(path/'skin.ini').write_text('[General]\nVersion: 2.5\n')
            fixtures=[('selection-mode',92,90,'button-like'),('selection-mode-over',1200,800,'composite-canvas'),
                      ('selection-mods',400,150,'decorated-button'),('selection-random',1,1,'transparent-replacement'),
                      ('selection-options',77,90,'button-like'),('selection-options@2x',150,180,'button-like')]
            for name,w,h,kind in fixtures:
                im=Image.new('RGBA',(w,h),(255,255,255,0 if w==h==1 or name=='selection-mods' else 255))
                if name=='selection-mods': ImageDraw.Draw(im).rectangle((20,80,59,129),fill=(255,255,255,255))
                im.save(path/(name+'.png'))
            corpus,_=scan('arbitrary',path);assets={r['filename']:r for r in corpus['assets']}
            for name,w,h,kind in fixtures:
                self.assertEqual(kind,assets[name+'.png']['classification'])
                self.assertNotIn('error',assets[name+'.png'])
            self.assertIn('giant',assets['selection-mode-over.png']['flags'])
            self.assertIn('large-transparent-margin',assets['selection-mods.png']['flags'])
            self.assertEqual([0,0,0,0],assets['selection-random.png']['interaction_content'])
            self.assertIn('fully-transparent',assets['selection-random.png']['flags'])
            for suffix in ['', '@2x']: self.assertIn('density-size-mismatch',assets['selection-options'+suffix+'.png']['flags'])
            self.assertIn('songselect-top',corpus['missing']);self.assertIn('songselect-bottom',corpus['missing'])

    def test_extreme_aspect_corruption_and_animation_are_reported_without_rejection(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)
            Image.new('RGBA',(2000,1),(255,255,255,255)).save(path/'menu-button-background.png')
            (path/'songselect-top.png').write_text('broken')
            for frame in [0,1,3]: Image.new('RGBA',(1,1)).save(path/f'menu-back-{frame}.png')
            corpus,_=scan('fixture',path)
            assets={r['filename']:r for r in corpus['assets']}
            self.assertIn('extreme-aspect',assets['menu-button-background.png']['flags'])
            self.assertIn('decode-failed',assets['songselect-top.png']['flags'])
            self.assertEqual(3,assets['menu-back-0.png']['animation_frames'])
            self.assertEqual(2,assets['menu-back-0.png']['contiguous_animation_frames'])

    def test_diff_counts_all_rgba_channels_and_records_changed_bounds(self):
        a=Image.new('RGBA',(10,10));b=a.copy();b.putpixel((3,4),(1,2,3,0));b.putpixel((7,8),(0,0,0,255))
        _,stats=difference(a,b)
        self.assertEqual(2,stats['changed_pixels']);self.assertEqual(.98,stats['equal_ratio'])
        self.assertEqual((3,4,8,9),stats['changed_bounds'])

    def test_equal_logical_canvases_do_not_claim_equal_raw_dimensions(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)
            for name,size in [('selection-mode.png',(92,90)),('selection-mode-over@2x.png',(184,180))]:
                Image.new('RGBA',size,(255,255,255,255)).save(path/name)
            _,files=scan('fixture',path)
            stats=diffs(files,path/'diff')[0]
            self.assertFalse(stats['same_raw_dimensions'])
            self.assertEqual(1,stats['equal_ratio'])

if __name__=='__main__': unittest.main()
