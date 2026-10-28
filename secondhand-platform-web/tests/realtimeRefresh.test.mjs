import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawn } from 'node:child_process'
import { createServer } from 'vite'

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..')
const chrome=[process.env.CHROME_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','/usr/bin/chromium','/usr/bin/google-chrome'].find(p=>p&&existsSync(p))
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function until(check) { for(let i=0;i<150;i++){if(await check()) return;await pause(100)} throw new Error('Timed out waiting for realtime state') }

test('real Vue lifecycle refreshes public product and private order state without losing drafts or pagination', {skip:!chrome,timeout:120000}, async()=>{
  const output=resolve(root,'target/realtime-tests');mkdirSync(output,{recursive:true})
  const dir=mkdtempSync(resolve(output,'run-'))
  let stock=3, requests=0, holdNext=false, held=[]
  let pendingCount={toAccept:0,toShip:1,toConfirm:0}
  const server=await createServer({root,logLevel:'error',server:{port:0,host:'127.0.0.1',open:false},plugins:[{
    name:'realtime-test-api',configureServer(server){server.middlewares.use((req,res,next)=>{
      const url=new URL(req.url,'http://test')
      if(url.pathname==='/api/order/pending-count' || url.pathname==='/api/order/100/deliver' || url.pathname==='/api/order/100/confirm') {
        if(url.pathname.endsWith('/deliver')) pendingCount={toAccept:0,toShip:0,toConfirm:1}
        if(url.pathname.endsWith('/confirm')) pendingCount={toAccept:0,toShip:0,toConfirm:0}
        res.setHeader('Content-Type','application/json');res.end(JSON.stringify({code:200,data:pendingCount}));return
      }
      if(url.pathname!=='/api/product/availability') return next()
      requests++
      const data=(url.searchParams.get('ids')||'').split(',').filter(Boolean).map(id=>({id:Number(id),stock,status:stock?1:3}))
      const body=JSON.stringify({code:200,data})
      const finish=()=>{res.setHeader('Content-Type','application/json');res.end(body)}
      if(holdNext){holdNext=false;held.push(finish)}else finish()
    })}
  }]})
  writeFileSync(resolve(dir,'index.html'),`<html><body><div id="app"></div><script>
    window.__sockets=[];window.__intervals=new Set();
    const nativeSocket=window.WebSocket,nativeInterval=window.setInterval,nativeClear=window.clearInterval;
    window.setInterval=(fn,delay)=>{const id=nativeInterval(fn,delay);__intervals.add(id);return id};
    window.clearInterval=id=>{__intervals.delete(id);nativeClear(id)};
    Object.defineProperty(document,'visibilityState',{configurable:true,get:()=>window.__visibility||'visible'});
    window.WebSocket=class {
      constructor(url){if(!String(url).endsWith('/ws/products')) return new nativeSocket(url);this.readyState=0;__sockets.push(this);setTimeout(()=>{this.readyState=1;this.onopen?.()},0)}
      send(text){if(text==='ping'){this.onmessage?.({data:'pong'});return}setTimeout(()=>this.onmessage?.({data:'{"type":"subscribed"}'}),0)}
      close(){this.readyState=3;this.onclose?.()}
      emit(id){this.onmessage?.({data:JSON.stringify({type:'product_changed',productId:id})})}
    };
    </script><script type="module">
    import {createApp,ref,h} from 'vue';import{createPinia}from'pinia';
    import{useProductAvailability}from'/src/composables/useProductAvailability.js';
    import{useOrderRefresh}from'/src/composables/useOrderRefresh.js';
    import{useMessageStore}from'/src/stores/message.js';
    import{useUserStore}from'/src/stores/user.js';
    import{createRouter,createMemoryHistory}from'vue-router';
    import AppHeader from '/src/components/common/AppHeader.vue';
    import{deliverOrder,confirmOrder}from'/src/api/order.js';
    let app=createApp({setup(){
      const product=ref({id:40,stock:9,status:1}),draft=ref('保留输入内容'),page=ref(2),order=ref(1),busy=ref(false);let orderLoads=0;
      const messages=useMessageStore();useUserStore().userInfo={id:2,username:'buyer'};
      useProductAvailability(()=>[product.value],rows=>Object.assign(product.value,rows[0]));
      useOrderRefresh(async()=>{orderLoads++;order.value=window.__nextOrder||1},n=>n.relatedId===100,()=>busy.value);
      window.__page={snapshot:()=>({stock:product.value.stock,status:product.value.status,draft:draft.value,page:page.value,order:order.value,orderLoads}),
        deliver:()=>deliverOrder(100),confirm:()=>confirmOrder(100),notify:()=>{messages.latestNotification={type:'order',relatedId:100}},busy:v=>busy.value=v,
        reconnect:()=>{messages.connected=false;setTimeout(()=>messages.connected=true,0)},unmount:()=>app.unmount()};
      return()=>h('div',[h(AppHeader),h('input',{value:draft.value}),h('span',String(product.value.stock))]);
    }});app.use(createPinia());app.use(createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>null}}]}));app.mount('#app');window.__ready=true;
    </script></body></html>`)
  let child,socket
  try {
    await server.listen();const port=server.httpServer.address().port
    child=spawn(chrome,['--headless=new','--no-sandbox','--disable-gpu','--no-first-run','--disable-background-networking','--remote-debugging-port=0',`--user-data-dir=${resolve(dir,'profile')}`,'about:blank'],{stdio:'ignore',windowsHide:true})
    const active=resolve(dir,'profile/DevToolsActivePort');await until(()=>existsSync(active))
    const debugPort=readFileSync(active,'utf8').split('\n')[0]
    const target=await(await fetch(`http://127.0.0.1:${debugPort}/json/new?about:blank`,{method:'PUT'})).json()
    socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j})
    let id=0;const pending=new Map()
    socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.method==='Runtime.exceptionThrown') console.error(JSON.stringify(m.params.exceptionDetails));if(pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}}
    const command=(method,params={})=>new Promise((resolve,reject)=>{const next=++id;pending.set(next,{resolve,reject});socket.send(JSON.stringify({id:next,method,params}))})
    const evaluate=async expression=>{const r=await command('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value}
    const path='/'+resolve(dir,'index.html').slice(root.length+1).replaceAll('\\','/')
    await command('Runtime.enable');
    await command('Page.navigate',{url:`http://127.0.0.1:${port}${path}`})
    await until(()=>evaluate('!!window.__ready'))
    const snapshot=()=>evaluate('__page.snapshot()')
    await until(async()=>(await snapshot()).stock===3)
    const badges=()=>evaluate('[...document.querySelectorAll(".order-alert")].map(el=>el.textContent.trim()).join("|")')
    await until(async()=>(await badges()).includes('待面交'))
    await evaluate('__page.deliver()')
    await until(async()=>(await badges()).includes('待确认') && !(await badges()).includes('待面交'))
    await evaluate('__page.confirm()')
    await until(async()=>(await badges())==='')
    pendingCount={toAccept:0,toShip:2,toConfirm:0}
    await evaluate('__page.notify()')
    await until(async()=>(await badges()).includes('待面交 2'))
    stock=2;holdNext=true;await evaluate('__sockets.at(-1).emit(40)');await until(()=>held.length===1)
    stock=1;await evaluate('__sockets.at(-1).emit(40)');held.shift()()
    await until(async()=>(await snapshot()).stock===1)
    stock=7;await evaluate("window.dispatchEvent(new Event('focus'))")
    await until(async()=>(await snapshot()).stock===7)
    await evaluate("__visibility='hidden';document.dispatchEvent(new Event('visibilitychange'))")
    assert.equal(await evaluate('__sockets.at(-1).readyState'),3)
    stock=0;await evaluate("__visibility='visible';document.dispatchEvent(new Event('visibilitychange'))")
    await until(async()=>(await snapshot()).status===3)
    await evaluate('__nextOrder=2;__page.notify()');await until(async()=>(await snapshot()).order===2)
    await evaluate('__page.busy(true);__nextOrder=3;__page.notify()');await pause(250)
    assert.equal((await snapshot()).order,2)
    await evaluate('__page.busy(false)');await until(async()=>(await snapshot()).order===3)
    await evaluate('__nextOrder=4;__page.reconnect()');await until(async()=>(await snapshot()).order===4)
    const state=await snapshot();assert.equal(state.page,2);assert.equal(state.draft,'保留输入内容')
    await evaluate('__page.unmount()');await pause(250);const before=requests
    await evaluate('__sockets.at(-1).emit(40)');await pause(250)
    assert.equal(requests,before);assert.equal(await evaluate('__intervals.size'),0)
  } finally {held.forEach(fn=>fn());socket?.close();child?.kill();await server.close()}
})
