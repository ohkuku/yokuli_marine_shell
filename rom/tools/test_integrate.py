"""临时目录内验证 ROM 集成器拒绝覆盖和拒绝被篡改的包，不运行 AOSP。"""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from integrate import integrate


class BundleProtectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'bundle'
        self.aosp = self.root / 'aosp'
        self.bundle.mkdir()
        (self.bundle / 'yokuli-build.json').write_text(json.dumps({'target': {'upstream_product': 'device/test.mk'}}))
        (self.bundle / 'Android.bp').write_text('fixture')
        files = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in self.bundle.iterdir()}
        (self.bundle / '.yokuli-managed.json').write_text(json.dumps({'schema_version': 1, 'files': files}))
        (self.aosp / 'build').mkdir(parents=True)
        (self.aosp / 'build/envsetup.sh').write_text('fixture')
        (self.aosp / 'device').mkdir()
        (self.aosp / 'device/test.mk').write_text('fixture')

    def test_first_install_and_same_bundle_are_idempotent(self):
        integrate(self.bundle, self.aosp)
        self.assertEqual(integrate(self.bundle, self.aosp), 'already integrated')

    def test_unowned_directory_is_preserved(self):
        target = self.aosp / 'vendor/yokuli'
        target.mkdir(parents=True)
        (target / 'user-data').write_text('keep')
        with self.assertRaises(ValueError):
            integrate(self.bundle, self.aosp)
        self.assertEqual((target / 'user-data').read_text(), 'keep')

    def test_edited_installed_file_is_preserved(self):
        integrate(self.bundle, self.aosp)
        target = self.aosp / 'vendor/yokuli/Android.bp'
        target.write_text('user-edit')
        with self.assertRaises(ValueError):
            integrate(self.bundle, self.aosp)
        self.assertEqual(target.read_text(), 'user-edit')

    def test_changed_incoming_file_is_rejected_before_copy(self):
        (self.bundle / 'Android.bp').write_text('tampered')
        with self.assertRaises(ValueError):
            integrate(self.bundle, self.aosp)
        self.assertFalse((self.aosp / 'vendor/yokuli').exists())

    def test_vendor_symlink_is_rejected_without_external_write(self):
        outside = self.root / 'outside'
        outside.mkdir()
        (self.aosp / 'vendor').symlink_to(outside, target_is_directory=True)
        with self.assertRaises(ValueError):
            integrate(self.bundle, self.aosp)
        self.assertEqual(list(outside.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
