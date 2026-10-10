import json
import hashlib
import sys
import subprocess
import os
import types
import shlex
import io
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
sys.path.insert(0, str(Path(__file__).resolve().parent))
import manual_dispatch as sender
import sync_announcement_preview
import serve_update_test
import preview_announcement_on_device

class AnnouncementContractsTest(unittest.TestCase):
    def arguments(self):
        return sender.argument_parser().parse_args(['--title','Test','--body','Exact **copy**','--type','in-app','--target','test_users','--test-mode','true'])
    def test_browser_preview_uses_the_app_font(self):
        sync_announcement_preview.sync(check=True)
    def test_real_delivery_requires_approval_for_exact_draft(self):
        args=self.arguments(); transport=Mock(return_value='test')
        with self.assertRaisesRegex(ValueError,'approve'):sender.send_notification(args,sender=transport)
        transport.assert_not_called()
        args.approval_digest=sender.plan_digest(sender.notification_plan(args))
        sender.send_notification(args,sender=transport)
        args.body='Edited copy'
        with self.assertRaises(ValueError):sender.send_notification(args,sender=transport)
        self.assertEqual(transport.call_count,1)
    def test_test_audience_accepted_and_release_filter_requires_explicit_override(self):
        args=self.arguments(); self.assertEqual(sender.notification_plan(args)['topic'],'test_users')
        args.test_mode='false';args.target='all_users';args.release_alert='true'
        with self.assertRaises(ValueError):sender.notification_plan(args)
        args.include_play='true';self.assertEqual(sender.notification_plan(args)['data']['include_play'],'true')
    def test_preview_contains_current_digest_and_never_calls_sender(self):
        args=self.arguments();args.preview_only='true';transport=Mock()
        result=sender.send_notification(args,sender=transport)
        transport.assert_not_called();self.assertEqual(result['approval_digest'],sender.plan_digest(sender.notification_plan(args)))
    def test_browser_and_workflow_approval_digest_are_identical(self):
        args=self.arguments();args.title='boxlore – 更新';args.body='Exact \n **é** copy'
        plan=sender.notification_plan(args)
        node_script="const {canonical}=require('./admin-panel/public/notifyappusers/dashboard.js');const crypto=require('node:crypto');let data='';process.stdin.on('data',x=>data+=x).on('end',()=>process.stdout.write(crypto.createHash('sha256').update(JSON.stringify(canonical(JSON.parse(data)))).digest('hex')));"
        result=subprocess.run(['node','-e',node_script],input=json.dumps(plan,ensure_ascii=False),text=True,capture_output=True,check=True,cwd=Path(__file__).resolve().parents[2])
        self.assertEqual(result.stdout,sender.plan_digest(plan))
    def test_direct_sender_excludes_play_and_test_topics_before_delivery(self):
        args=self.arguments();args.target='direct_users';args.test_mode='false'
        plan=sender.notification_plan(args)
        messaging=Mock();messaging.send.return_value='fixture-id'
        firebase=types.ModuleType('firebase_admin');firebase.initialize_app=Mock();firebase.credentials=Mock();firebase.messaging=messaging
        with patch.dict(sys.modules,{'firebase_admin':firebase}),patch.dict(os.environ,{'FIREBASE_CREDENTIALS':'{}'}):
            sender._firebase_send(plan)
        delivered=messaging.Message.call_args.kwargs
        self.assertNotIn('topic',delivered)
        self.assertEqual("'direct_users' in topics && !('play_users' in topics) && !('test_users' in topics)",delivered['condition'])
    def test_ordinary_sender_default_does_not_use_legacy_play_suppressed_release_category(self):
        plan=sender.notification_plan(self.arguments())
        self.assertEqual('ANNOUNCEMENT',plan['data']['category'])
        self.assertEqual('false',plan['data']['release_alert'])
    def test_insecure_image_is_rejected_without_dispatch(self):
        args=self.arguments();args.image='http://example.com/image.png'
        with self.assertRaisesRegex(ValueError,'HTTPS'):sender.notification_plan(args)
    def test_local_device_preview_uses_only_adb_and_requires_receiver_success(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            data={'title':'Local test','body':"Listener's `copy` with $(symbols) and Unicode 更新"}
            path=Path(directory)/'preview.json';path.write_text(json.dumps({'data':data}))
            runner=Mock(return_value=subprocess.CompletedProcess([],0,'Broadcast completed: result=-1',''))
            preview_announcement_on_device.preview(path,runner=runner)
            command=runner.call_args.args[0]
            remote=shlex.split(command[-1])
            self.assertEqual('adb',command[0]);self.assertEqual('shell',command[-2])
            self.assertIn('cx.aswin.boxlore/.testing.AnnouncementTestReceiver',remote)
            self.assertEqual(data,json.loads(remote[-1]))
            runner.return_value=subprocess.CompletedProcess([],0,'Broadcast completed: result=0','')
            with self.assertRaisesRegex(ValueError,'isolated'):preview_announcement_on_device.preview(path,runner=runner)
    def test_loopback_fixture_is_explicitly_marked_test_only(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'test.apk';path.write_bytes(b'fixture')
            value=serve_update_test.manifest(path,29)
            self.assertTrue(value['testOnly']);self.assertEqual(value['apkUrl'],'http://127.0.0.1:8765/candidate.apk')
    def test_local_update_fixture_serves_exact_bytes_and_resumes_a_partial_download(self):
        import tempfile
        class Connection:
            def __init__(self,request): self.request=request;self.response=bytearray()
            def makefile(self,*args): return io.BytesIO(self.request)
            def sendall(self,data): self.response.extend(data)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'test.apk';path.write_bytes(b'approved-candidate-bytes')
            Handler=serve_update_test.handler(path,29,'available')
            Handler.log_message=lambda *args:None
            for headers,status,expected in [(b'',b'200',path.read_bytes()),(b'Range: bytes=9-\r\n',b'206',path.read_bytes()[9:])]:
                connection=Connection(b'GET /candidate.apk HTTP/1.0\r\n'+headers+b'\r\n')
                Handler(connection,('127.0.0.1',0),Mock())
                header,body=bytes(connection.response).split(b'\r\n\r\n',1)
                self.assertIn(status,header.split(b'\r\n')[0]);self.assertEqual(expected,body)
    def test_long_local_notes_are_preserved_in_manifest_and_browser_view(self):
        import tempfile
        class Connection:
            def __init__(self, route): self.route=route;self.response=bytearray()
            def makefile(self,*args): return io.BytesIO(f'GET {self.route} HTTP/1.0\r\n\r\n'.encode())
            def sendall(self,data): self.response.extend(data)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'test.apk';path.write_bytes(b'candidate')
            notes='Sample notes — scrolling test.\n'*500+'End of test release notes.'
            Handler=serve_update_test.handler(path,29,'available',notes);Handler.log_message=lambda *args:None
            for route in ('/update.json','/notes'):
                connection=Connection(route);Handler(connection,('127.0.0.1',0),Mock())
                header,body=bytes(connection.response).split(b'\r\n\r\n',1)
                self.assertIn(b'200',header.split(b'\r\n')[0])
                self.assertEqual(notes,json.loads(body)['notes'] if route=='/update.json' else body.decode('utf-8'))
    def test_local_notes_respect_android_character_and_utf8_response_limits(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'test.apk';path.write_bytes(b'candidate')
            for notes in ('a'*24_001,'🎧'*12_001):
                with self.assertRaisesRegex(ValueError,'24,000'):serve_update_test.manifest(path,29,notes=notes)
            with self.assertRaisesRegex(ValueError,'64 KiB'):serve_update_test.manifest(path,29,notes='界'*23_000)
    def test_local_update_fixture_exposes_error_incompatible_and_integrity_test_states(self):
        import tempfile
        class Connection:
            def __init__(self): self.response=bytearray()
            def makefile(self,*args): return io.BytesIO(b'GET /update.json HTTP/1.0\r\n\r\n')
            def sendall(self,data): self.response.extend(data)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'test.apk';path.write_bytes(b'candidate')
            for state in ('unavailable','incompatible','corrupt'):
                Handler=serve_update_test.handler(path,29,state);Handler.log_message=lambda *args:None
                connection=Connection();Handler(connection,('127.0.0.1',0),Mock())
                header,body=bytes(connection.response).split(b'\r\n\r\n',1)
                if state=='unavailable':self.assertIn(b'404',header.split(b'\r\n')[0])
                elif state=='incompatible':self.assertEqual(99,json.loads(body)['minSdk'])
                else:self.assertNotEqual(hashlib.sha256(path.read_bytes()).hexdigest(),json.loads(body)['apkSha256'])
    def test_release_publication_only_previews_and_play_bundle_remains_separate(self):
        workflow=(Path(__file__).resolve().parents[2]/'.github/workflows/changelog-on-merge.yml').read_text()
        announce=workflow.split('  announce-release:')[1]
        self.assertIn("preview_only: 'true'",announce);self.assertIn('target: direct_users',announce)
        self.assertIn('bundlePlayRelease',workflow)
