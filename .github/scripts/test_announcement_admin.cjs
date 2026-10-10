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
