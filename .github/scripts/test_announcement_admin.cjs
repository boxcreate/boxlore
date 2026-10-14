const test = require('node:test');
const assert = require('node:assert/strict');
const {plan, canonical, PALETTES, isPreviewExcluded, parsePreview, reviewRows} = require('../../admin-panel/public/notifyappusers/dashboard.js');
const base = () => ({title:'boxlore test',body:'First line\nSecond line',type:'both',sound:'silent',action_label:'Open',show_action_in_push:'false',show_action_in_app:'true',category:'ANNOUNCEMENT',presentation:'fullscreen',tone:'primary',image_style:'banner',release_alert:'false',include_play:'false',release_version_code:'0',test_mode:'true',target:'test_users',dry_run:'true',collapse_key:'',route:'',image:''});
test('isolated audience and legacy payload retained',()=>{const result=plan(base());assert.equal(result.topic,'test_users');assert.equal(result.data.body,'First line\nSecond line');assert.equal(result.data.presentation,'fullscreen');});
test('test mode rejects production and Play release override is explicit',()=>{for(const target of ['all_users','prod_users','direct_users'])assert.throws(()=>plan({...base(),target}));assert.throws(()=>plan({...base(),test_mode:'false',target:'all_users',release_alert:'true'}));assert.equal(plan({...base(),test_mode:'false',target:'all_users',release_alert:'true',include_play:'true'}).topic,'all_users');});
test('UTF-8 bounds prevent oversized live request',()=>assert.throws(()=>plan({...base(),body:'é'.repeat(1100)})));
test('canonical approval preserves exact Unicode text and sorted keys',()=>{assert.equal(JSON.stringify(canonical({b:{z:1,a:2},a:'é'})), '{"a":"é","b":{"a":2,"z":1}}');});
test('preview classic palettes match actual tokens',()=>{assert.equal(PALETTES.dark.primary[0],'#9C8CFF');assert.equal(PALETTES.light.container,'#EDEDF4');});

test('release preview retains GitHub page and rejects other destinations',()=>{const draft={...base(),release_alert:'true',release_url:'https://github.com/boxcreate/boxlore/releases/tag/v29'};assert.equal(plan(draft).data.release_url,draft.release_url);assert.throws(()=>plan({...draft,release_url:'https://evil.example/release'}));});

test('Play preview shows no release alert unless explicitly included',()=>{const release={...base(),release_alert:'true'};assert.equal(isPreviewExcluded(release,true),true);assert.equal(isPreviewExcluded(release,false),false);assert.equal(isPreviewExcluded({...release,include_play:'true'},true),false);assert.equal(isPreviewExcluded(base(),true),false);});

test('import validates a preview before editing and clears omitted optional destinations',()=>{assert.throws(()=>parsePreview({}));assert.throws(()=>parsePreview({draft:{title:'Broken',body:'Not a preview'}}));const draft=base();const preview=plan(draft);const restored=parsePreview(preview);assert.equal(restored.title,draft.title);assert.equal(restored.route,'');assert.equal(restored.image,'');assert.equal(restored.release_url,'');assert.equal(restored.dry_run,'true');});

test('push-only messages do not promise an in-app alert',()=>{assert.equal(isPreviewExcluded({...base(),type:'push'},false),true);assert.equal(isPreviewExcluded({...base(),type:'push'},true),true);assert.equal(isPreviewExcluded({...base(),type:'in-app'},false),false);});


test('push-only review describes notification actions and no in-app buttons',()=>{const draft={...base(),type:'push',release_alert:'true'};const rows=Object.fromEntries(reviewRows(draft,'Isolated test builds'));assert.equal(rows['Appearance'],'Android system notification');assert.equal(rows['In-app actions'],'No in-app alert');assert.equal(rows['Notification action'],'None');assert.equal(rows['Delivery'],'System notification');assert.equal(rows['Send mode'],'Provider validation only — no delivery');});
test('combined delivery review includes the configured notification action',()=>{const draft={...base(),route:'https://example.org',show_action_in_push:'true'};const rows=Object.fromEntries(reviewRows(draft,'Isolated test builds'));assert.equal(rows['In-app actions'],'Open · Dismiss');assert.equal(rows['Notification action'],'Open');assert.equal(rows['Action destination'],'https://example.org');});

test('preview sizing isolates the legacy gateway frame and fits the entire viewport',()=>{
 const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
 const root=path.join(__dirname,'../../admin-panel/public/notifyappusers');
 const html=fs.readFileSync(path.join(root,'dashboard.html'),'utf8');
 const css=fs.readFileSync(path.join(root,'composer.css'),'utf8');
 assert.ok(html.includes('class="preview-phone"'));
 assert.ok(css.includes('.preview-phone{'));
 assert.ok(!html.includes('class="phone-frame"'));
 const device={style:{}},frame={style:{width:'260px',height:'520px'}};
 const stage={clientWidth:420,clientHeight:650,querySelector:selector=>({'.preview-device':device,'.preview-phone':frame})[selector]};
 const nodes={previewStage:stage,previewWidth:{value:'393'},previewHeight:{value:'600'},previewZoom:{value:'fit'},reviewDialog:{open:false}};
 const context=vm.createContext({TextEncoder,document:{getElementById:id=>nodes[id]}});
 vm.runInContext(fs.readFileSync(path.join(root,'dashboard.js'),'utf8'),context);
 for(const width of [360,393,430])for(const height of [600,740,850]){
  nodes.previewWidth.value=String(width);nodes.previewHeight.value=String(height);context.fitPreview();
  assert.equal(frame.style.width,(width+18)+'px');assert.equal(frame.style.height,(height+18)+'px');
  assert.ok(parseFloat(device.style.width)<=stage.clientWidth-28);
  assert.ok(parseFloat(device.style.height)<=stage.clientHeight-28);
 }
 stage.clientWidth=0;stage.clientHeight=0;context.fitPreview();
 assert.equal(frame.style.transform,'scale(0)');
 nodes.previewZoom.value='1';context.fitPreview();
 assert.equal(frame.style.transform,'scale(1)');
 assert.equal(device.style.width,'448px');assert.equal(device.style.height,'868px');
});

test('browser preview formats the native Markdown subset without interpreting HTML',()=>{
 const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
 class Node { constructor(tag,text=''){this.tag=tag;this.textContent=text;this.children=[];this.dataset={};} append(...nodes){this.children.push(...nodes);} replaceChildren(){this.children=[];} }
 const context=vm.createContext({document:{createElement:tag=>new Node(tag),createTextNode:text=>new Node('#text',text)},location:{origin:'https://boxcasts.web.app'},window:{addEventListener(){},parent:{postMessage(){}}}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../admin-panel/public/notifyappusers/announcements/renderer.js'),'utf8'),context);
 const root=new Node('root');
 context.content(root,'## Heading\nFirst **bold** line\nSecond _italic_ [docs](https://example.org)\n\n1) One\n+ Two\n> A callout\n`code` <b>plain HTML</b>');
 assert.deepEqual(root.children.map(n=>n.tag),['h2','p','div','div','blockquote','p']);
 assert.equal(root.children[0].dataset.level,'2');
 assert.equal(root.children[1].children.find(n=>n.tag==='strong').textContent,'bold');
 assert.equal(root.children[1].children.find(n=>n.tag==='em').textContent,'italic');
 const link=root.children[1].children.find(n=>n.tag==='a');assert.equal(link.href,'https://example.org');assert.equal(link.rel,'noopener noreferrer');
 assert.equal(root.children[2].children[0].textContent,'1.');assert.equal(root.children[3].children[0].textContent,'•');
 assert.equal(root.children[5].children.find(n=>n.tag==='code').textContent,'code');
 assert.ok(root.children[5].children.some(n=>n.textContent.includes('<b>plain HTML</b>')));
 context.inline(root,'[bad](javascript:alert(1))');assert.equal(root.children.filter(n=>n.tag==='a').length,0);
});


test('incomplete URLs keep the composer guidance instead of a browser TypeError',()=>{
 for(const release_url of ['github.com/x','https://'])assert.throws(()=>plan({...base(),release_alert:'true',release_url}),{message:'Use a boxlore GitHub release page.'});
 for(const image of ['example.com/image.png','https://'])assert.throws(()=>plan({...base(),image}),{message:'Use an HTTPS image URL without embedded credentials.'});
});

test('preview messages require the same origin and the expected parent',()=>{
 const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
 const origin='https://boxcasts.web.app',handlers={},sent=[],rendered=[];
 const parent={postMessage:(message,target)=>sent.push({message,target})};
 const context=vm.createContext({location:{origin},window:{parent,addEventListener:(name,handler)=>handlers[name]=handler}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../admin-panel/public/notifyappusers/announcements/renderer.js'),'utf8'),context);
 context.window.renderAnnouncement=(payload)=>rendered.push(payload);
 const data={source:'boxlore-preview',payload:{title:'Test'}};
 handlers.message({origin:'https://other.example',source:parent,data});
 handlers.message({origin,source:{},data});
 handlers.message({origin,source:parent,data:{...data,source:'other'}});
 assert.equal(rendered.length,0);
 handlers.message({origin,source:parent,data});
 assert.equal(rendered.length,1);assert.equal(rendered[0].title,'Test');
 context.notify('dismiss');assert.equal(sent.length,2);
 assert.ok(sent.every(message=>message.target===origin));
});


test('ordinary drafts omit the hidden release page across workflow dispatch and digest validation',()=>{
 const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
 const {spawnSync}=require('node:child_process');
 const releaseUrl='https://github.com/boxcreate/boxlore/releases/latest';
 const draft={...base(),release_url:releaseUrl};
 const ids={title:'inputTitle',body:'inputBody',type:'inputType',route:'inputRoute',image:'inputImage',target:'inputTarget',collapse_key:'inputCollapseKey',sound:'inputSound',action_label:'inputActionLabel',category:'inputCategory',presentation:'inputPresentation',tone:'inputTone',image_style:'inputImageStyle',release_version_code:'inputReleaseCode',release_url:'inputReleaseUrl'};
 const bools={dry_run:'inputDryRun',test_mode:'inputTestMode',show_action_in_push:'inputShowPush',show_action_in_app:'inputShowApp',release_alert:'inputReleaseAlert',include_play:'inputIncludePlay'};
 const nodes={};
 for(const[key,id]of Object.entries(ids))nodes[id]={value:draft[key]};
 for(const[key,id]of Object.entries(bools))nodes[id]={checked:draft[key]==='true'};
 const context=vm.createContext({TextEncoder,document:{getElementById:id=>nodes[id]}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../admin-panel/public/notifyappusers/dashboard.js'),'utf8'),context);
 function validateWorkflow(normalized){
  const browserPlan=plan(normalized);
  const script="import json,sys; sys.path.insert(0,'.github/scripts'); import manual_dispatch as sender; draft=json.load(sys.stdin); argv=[part for key,value in draft.items() for part in ('--'+key.replace('_','-'),value)]; plan=sender.notification_plan(sender.argument_parser().parse_args(argv)); print(json.dumps({'plan':plan,'digest':sender.plan_digest(plan)},ensure_ascii=False))";
  const result=spawnSync('python3',['-B','-c',script],{cwd:path.join(__dirname,'../..'),input:JSON.stringify(normalized),encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
  const workflow=JSON.parse(result.stdout);
  assert.deepEqual(workflow.plan,browserPlan);
  const expected=require('node:crypto').createHash('sha256').update(JSON.stringify(canonical(browserPlan))).digest('hex');
  assert.equal(workflow.digest,expected);
 }
 const ordinary=context.readDraft();
 assert.equal(ordinary.release_url,'');
 validateWorkflow(ordinary);
 nodes.inputReleaseAlert.checked=true;
 const release=context.readDraft();
 assert.equal(release.release_url,releaseUrl);
 validateWorkflow(release);
 nodes.inputReleaseAlert.checked=false;
 const switched=context.readDraft();
 assert.equal(switched.release_url,'');
 validateWorkflow(switched);
 assert.equal(nodes.inputReleaseUrl.value,releaseUrl,'Retain the entered page for switching back to release mode');
});

const {latestReleaseDraft,previewFromArchive}=require('../../admin-panel/public/notifyappusers/dashboard.js');
const cryptoApi=require('node:crypto');
const releaseBase='https://api.github.com/repos/boxcreate/boxlore';
const latest={id:29,tag_name:'v0.0.29',draft:false,prerelease:false};
function releasePreview(tag='v0.0.29'){
 const draft={...base(),test_mode:'false',target:'direct_users',dry_run:'false',release_alert:'true',release_url:`https://github.com/boxcreate/boxlore/releases/tag/${tag}`,release_version_code:'29',type:'in-app'};
 const value=plan(draft);
 return {...value,mode:'offline-preview',sent:false,approval_digest:cryptoApi.createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex')};
}
function previewZip(preview,{method=8,name='announcement-preview.json',flags=0}={}){
 const data=Buffer.from(JSON.stringify(preview)),filename=Buffer.from(name),packed=method===8?require('node:zlib').deflateRawSync(data):data;
 const local=Buffer.alloc(30);local.writeUInt32LE(0x04034b50);local.writeUInt16LE(flags,6);local.writeUInt16LE(method,8);local.writeUInt32LE(packed.length,18);local.writeUInt32LE(data.length,22);local.writeUInt16LE(filename.length,26);
 const central=Buffer.alloc(46);central.writeUInt32LE(0x02014b50);central.writeUInt16LE(flags,8);central.writeUInt16LE(method,10);central.writeUInt32LE(packed.length,20);central.writeUInt32LE(data.length,24);central.writeUInt16LE(filename.length,28);
 const end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(1,8);end.writeUInt16LE(1,10);end.writeUInt32LE(central.length+filename.length,12);end.writeUInt32LE(local.length+filename.length+packed.length,16);
 return Buffer.concat([local,filename,packed,central,filename,end]);
}
function releaseFixture({preview=releasePreview(),release=latest,artifacts,run,zip,status=200,changedRelease}={}){
 const archive=zip||previewZip(preview);
 const artifact={id:10,name:'announcement-preview',expired:false,size_in_bytes:archive.length,workflow_run:{id:100,head_branch:'master'},digest:'sha256:'+cryptoApi.createHash('sha256').update(archive).digest('hex')};
 const calls=[];let releaseReads=0;
 const fetcher=async(url,options)=>{
  calls.push({url,options});assert.equal(options.method,'GET');assert.equal(options.headers.Authorization,'token fixture-token');
  const endpoint=url.slice(releaseBase.length);
  if(status!==200)return new Response('{}',{status});
  if(endpoint==='/releases/latest')return Response.json(releaseReads++&&changedRelease?changedRelease:release);
  if(endpoint.startsWith('/actions/artifacts?'))return Response.json(artifacts||{total_count:1,artifacts:[artifact]});
  if(endpoint==='/actions/runs/100')return Response.json(run||{path:'.github/workflows/changelog-on-merge.yml',status:'completed',conclusion:'success',head_branch:'master'});
  if(endpoint==='/actions/artifacts/10/zip')return new Response(archive);
  throw Error('Unexpected request: '+endpoint);
 };
 return {calls,fetcher,artifact};
}

test('release fetch validates the published tag, both digests and the existing audience without sending',async()=>{
 const fixture=releaseFixture();const result=await latestReleaseDraft('fixture-token',fixture.fetcher);
 assert.equal(result.tag,latest.tag_name);assert.equal(result.draft.target,'direct_users');assert.equal(result.draft.presentation,'fullscreen');assert.equal(result.draft.dry_run,'false');
 assert.equal(fixture.calls.length,5);assert.ok(fixture.calls.every(c=>c.options.method==='GET'));
});
test('stored and deflated GitHub preview archives preserve exact copy',async()=>{
 const preview=releasePreview();for(const method of [0,8])assert.deepEqual(await previewFromArchive(previewZip(preview,{method})),preview);
});
test('bad archive names, encryption, oversized data and malformed archives fail closed',async()=>{
 for(const bytes of [new Uint8Array(1),new Uint8Array(131073),previewZip(releasePreview(),{name:'other.json'}),previewZip(releasePreview(),{flags:1}),previewZip(releasePreview(),{method:99}),previewZip({text:'x'.repeat(65537)})])await assert.rejects(previewFromArchive(bytes));
});
test('missing, expired, manual and failed previews never become a release draft',async()=>{
 const missing=releaseFixture({artifacts:{total_count:0,artifacts:[]}});assert.equal(await latestReleaseDraft('fixture-token',missing.fetcher),null);
 const fixture=releaseFixture();fixture.artifact.expired=true;assert.equal(await latestReleaseDraft('fixture-token',fixture.fetcher),null);
 for(const run of [{path:'.github/workflows/send-notification.yml',status:'completed',conclusion:'success',head_branch:'master'},{path:'.github/workflows/changelog-on-merge.yml',status:'completed',conclusion:'failure',head_branch:'master'}])assert.equal(await latestReleaseDraft('fixture-token',releaseFixture({run}).fetcher),null);
 assert.equal(await latestReleaseDraft('fixture-token',releaseFixture({preview:releasePreview('v0.0.28')}).fetcher),null);
 assert.equal(await latestReleaseDraft('fixture-token',releaseFixture({status:404}).fetcher),null);
});
test('permission failure, stale release and edited preview approval provide recovery',async()=>{
 await assert.rejects(latestReleaseDraft('fixture-token',releaseFixture({status:403}).fetcher),/Sign in again/);
 await assert.rejects(latestReleaseDraft('fixture-token',releaseFixture({changedRelease:{...latest,id:30,tag_name:'v0.0.30'}}).fetcher),/newer release/);
 const preview=releasePreview();preview.data.body='Changed after approval';await assert.rejects(latestReleaseDraft('fixture-token',releaseFixture({preview}).fetcher),/failed validation/);
 const fixture=releaseFixture();fixture.artifact.digest='sha256:bad';await assert.rejects(latestReleaseDraft('fixture-token',fixture.fetcher),/checksum/);
});
test('artifact pagination continues past a page containing no eligible preview',async()=>{
 const fixture=releaseFixture();let pages=0;
 const fetcher=async(url,options)=>{if(url.includes('/actions/artifacts?')&&++pages===1)return Response.json({total_count:101,artifacts:[{...fixture.artifact,expired:true}]});return fixture.fetcher(url,options);};
 assert.equal((await latestReleaseDraft('fixture-token',fetcher)).tag,latest.tag_name);assert.equal(pages,2);
});

function draftUi({signedIn=true}={}){
 const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
 const nodes={releaseDraftStatus:{textContent:''},checkReleaseDraft:{disabled:false},reviewDialog:{open:false,close(){this.open=false;}},releaseDraftDialog:{open:false,showModal(){this.open=true;},close(){this.open=false;}},releaseDraftHeading:{focus(){}},releaseDraftMessage:{},loadReleaseDraft:{},reviewReleaseDraft:{},closeReleaseDraft:{},inputTitle:{focus(){}},approveDraft:{checked:true}};
 const initial=base(),state={draft:initial,loads:0};
 const context=vm.createContext({URL,TextEncoder,TextDecoder,Blob,Response,DecompressionStream,AbortSignal,crypto:cryptoApi.webcrypto,document:{getElementById:id=>nodes[id]},window:{__ADMIN_PAT:signedIn?'fixture-token':null}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../admin-panel/public/notifyappusers/dashboard.js'),'utf8'),context);
 context.readDraft=()=>state.draft;context.loadDraft=value=>{state.draft=value;state.loads++;nodes.approveDraft.checked=false;};
 return {context,nodes,state};
}
test('authenticated opening fills the editor and opens the popup without approving or sending',async()=>{
 const {context,nodes,state}=draftUi();const fixture=releaseFixture();context.fetch=fixture.fetcher;
 await context.checkReleaseDraft();assert.equal(state.loads,1,nodes.releaseDraftStatus.textContent);assert.equal(state.draft.release_url,'https://github.com/boxcreate/boxlore/releases/tag/v0.0.29');assert.equal(nodes.releaseDraftDialog.open,true);assert.equal(nodes.approveDraft.checked,false);assert.equal(nodes.loadReleaseDraft.hidden,true);assert.equal(nodes.reviewReleaseDraft.hidden,false);
 await context.checkReleaseDraft();assert.equal(state.loads,1);
 assert.ok(nodes.releaseDraftStatus.textContent.includes('edits are unchanged'));assert.ok(fixture.calls.every(c=>c.options.method==='GET'));
});
test('an edit or active review made while fetching is preserved until Load release draft',async()=>{
 for(const reviewing of [false,true]){
  const {context,nodes,state}=draftUi();let resolve;context.latestReleaseDraft=()=>new Promise(r=>resolve=r);
  const pending=context.checkReleaseDraft();if(reviewing)nodes.reviewDialog.open=true;else state.draft={...state.draft,body:'My unsent message'};
  resolve({key:'10',tag:latest.tag_name,draft:parsePreview(releasePreview())});await pending;
  assert.equal(state.loads,0);assert.equal(nodes.loadReleaseDraft.hidden,false);assert.equal(nodes.reviewReleaseDraft.hidden,true);
  context.closeReview=()=>{nodes.reviewDialog.open=false;};context.useReleaseDraft();assert.equal(state.loads,1);assert.equal(nodes.releaseDraftDialog.open,false);assert.equal(nodes.reviewDialog.open,false);
 }
});
test('offline openings and sign-out during a fetch never load a draft',async()=>{
 const offline=draftUi({signedIn:false});offline.context.fetch=()=>{throw Error('Offline mode must not call GitHub');};await offline.context.checkReleaseDraft();assert.equal(offline.state.loads,0);
 const {context,state,nodes}=draftUi();let resolve;context.latestReleaseDraft=()=>new Promise(r=>resolve=r);const pending=context.checkReleaseDraft();context.window.__ADMIN_PAT=null;resolve({key:'10',tag:latest.tag_name,draft:parsePreview(releasePreview())});await pending;assert.equal(state.loads,0);assert.equal(nodes.releaseDraftDialog.open,false);
});
test('release lookup errors leave the message intact and let the user retry',async()=>{
 const {context,nodes,state}=draftUi();context.latestReleaseDraft=async()=>{throw Error('Could not check release drafts. Try again.');};await context.checkReleaseDraft();assert.equal(state.loads,0);assert.equal(nodes.checkReleaseDraft.disabled,false);assert.match(nodes.releaseDraftStatus.textContent,/Try again/);
});
