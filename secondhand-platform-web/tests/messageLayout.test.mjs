import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { spawn } from 'node:child_process'
import * as sass from 'sass'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const chrome = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome', '/usr/bin/chromium'].find(path => path && existsSync(path))
const output = resolve(root, 'target/message-layout')
mkdirSync(output, { recursive: true })
const style = path => {
  const source = readFileSync(resolve(root, path), 'utf8').match(/<style[^>]*>([\s\S]*?)<\/style>/)[1]
  return sass.compileString(source.replaceAll("'@/", "'src/"), { loadPaths: [root], quietDeps: true }).css
}

async function render(file, dir, width, height) {
  const child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--no-first-run',
    '--disable-background-networking', '--remote-debugging-port=0', `--user-data-dir=${resolve(dir, 'profile')}`,
    'about:blank'], { stdio: 'ignore' })
  let socket
  try {
    const portFile = resolve(dir, 'profile/DevToolsActivePort')
    for (let i = 0; !existsSync(portFile) && i < 100; i++) await new Promise(r => setTimeout(r, 100))
    const port = readFileSync(portFile, 'utf8').split('\n')[0]
    const page = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json()
    socket = new WebSocket(page.webSocketDebuggerUrl)
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
    let nextId = 0
    const pending = new Map()
    socket.onmessage = ({ data }) => {
      const message = JSON.parse(data)
      const request = pending.get(message.id)
      if (request) {
        pending.delete(message.id)
        message.error ? request.reject(new Error(message.error.message)) : request.resolve(message.result)
      }
    }
    const command = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++nextId
      pending.set(id, { resolve, reject })
      socket.send(JSON.stringify({ id, method, params }))
    })
    // 直接指定内容视口，避免把 Windows 窗口边框尺寸写死在测试里。
    await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 640 })
    await command('Page.navigate', { url: pathToFileURL(file).href })
    let value
    for (let i = 0; i < 100; i++) {
      const result = await command('Runtime.evaluate', { expression: 'document.getElementById("layout-result")?.textContent', returnByValue: true })
      value = result.result?.value
      if (value) break
      await new Promise(r => setTimeout(r, 50))
    }
    const screenshot = await command('Page.captureScreenshot', { format: 'png' })
    writeFileSync(resolve(dir, 'layout.png'), Buffer.from(screenshot.data, 'base64'))
    return JSON.parse(value || '{}')
  } finally {
    socket?.close()
    child.kill()
  }
}

// 渲染实际组件 SCSS，填充模拟长列表，验证几何与滚动行为，不向真实账号发送消息。
for (const [width, height] of [[1440, 900], [1024, 600], [390, 844], [390, 650]]) {
  test(`message areas scroll independently at ${width}x${height}`, { skip: !chrome, timeout: 30000 }, async () => {
    const dir = mkdtempSync(resolve(output, 'run-'))
    const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <style>*{box-sizing:border-box}body{margin:0}header.site-header{height:${width < 640 ? 180 : 96}px;flex-shrink:0}
      ${readFileSync(resolve(root, 'node_modules/element-plus/theme-chalk/el-button.css'), 'utf8')}
      ${style('src/layouts/DefaultLayout.vue')}${style('src/views/message/MessageCenterPage.vue')}
      .composer textarea{height:60px;width:100%}.composer button{height:40px}
      .conversation-item{height:76px}.message-bubble{padding:16px}.chat-header{min-height:64px}
      </style><div class="default-layout default-layout--chat"><header class="site-header">Navigation</header>
      <main class="default-layout__main"><main class="messages-page messages-page--chat">
      <div class="message-tabs"><button class="el-button">私聊（12）</button><button class="el-button">系统通知（34）</button></div><section class="messages-shell">
      <aside class="conversation-pane"><div class="pane-header"><h1>Messages</h1></div><div class="connection-row">Connected</div><div class="conversation-list"></div></aside>
      <section class="chat-pane"><header class="chat-header">Selected conversation</header><div class="chat-body"><div class="message-list"></div></div>
      <form class="composer"><textarea aria-label="Message"></textarea><button>Send</button></form></section></section></main></main></div>
      <pre id="layout-result"></pre><script>
      const shell=document.querySelector('.messages-shell'), list=document.querySelector('.conversation-list'), chat=document.querySelector('.chat-body'), messages=document.querySelector('.message-list');
      list.innerHTML='<button class="conversation-item">One user</button>';messages.innerHTML='<div class="message-row"><div class="message-bubble">One message</div></div>';
      const before=shell.getBoundingClientRect().height;
      list.innerHTML=Array.from({length:80},(_,i)=>'<button class="conversation-item">User '+i+'</button>').join('');
      messages.innerHTML=Array.from({length:150},(_,i)=>'<div class="message-row"><div class="message-bubble"><p>Message '+i+'</p><time>12:00</time></div></div>').join('');
      const after=shell.getBoundingClientRect().height, composer=document.querySelector('.composer').getBoundingClientRect();
      const tabs=[...document.querySelectorAll('.message-tabs button')].map(button=>button.getBoundingClientRect());
      const pageRoot=document.querySelector('.messages-page');
      const layoutRoot=document.querySelector('.default-layout');
      pageRoot.classList.remove('messages-page--chat');layoutRoot.classList.remove('default-layout--chat');
      const notificationTabTop=document.querySelector('.message-tabs button').getBoundingClientRect().top;
      pageRoot.classList.add('messages-page--chat');layoutRoot.classList.add('default-layout--chat');
      chat.scrollTop=100;const chatTop=chat.scrollTop;
      if(innerWidth>860)list.scrollTop=100;else list.scrollLeft=100;
      const report={before,after,viewport:innerHeight,bottom:composer.bottom,chatHeight:chat.clientHeight,
      chatOverflows:chat.scrollHeight>chat.clientHeight,chatScrolls:chatTop>0,
      listScrolls:innerWidth>860?list.scrollTop>0:list.scrollLeft>0,independent:chat.scrollTop===chatTop,
      pageHeight:document.querySelector('.default-layout').getBoundingClientRect().height,
      tabGap:tabs[1].left-tabs[0].right,tabAlignment:Math.abs(tabs[0].top-tabs[1].top),
      tabTopGap:tabs[0].top-document.querySelector('.site-header').getBoundingClientRect().bottom,
      tabLeftGap:tabs[0].left,tabBottomGap:shell.getBoundingClientRect().top-tabs[0].bottom,
      notificationTabOffset:notificationTabTop-tabs[0].top};
      document.getElementById('layout-result').textContent=JSON.stringify(report);
      </script>`
    const file = resolve(dir, 'layout.html')
    writeFileSync(file, html)
    const report = await render(file, dir, width, height)
    assert.ok(Math.abs(report.tabGap - 8) < 1 && report.tabAlignment < 1, `tab spacing incorrect: ${JSON.stringify(report)}`)
    assert.ok(report.tabTopGap >= 12 && report.tabLeftGap >= 16 && report.tabBottomGap >= 12,
      `tabs touch navigation or chat panel: ${JSON.stringify(report)}`)
    assert.ok(Math.abs(report.notificationTabOffset) < 1, `tabs move when switching to notifications: ${JSON.stringify(report)}`)
    assert.ok(Math.abs(report.after - report.before) < 2, `content grows shell: ${JSON.stringify(report)}`)
    assert.ok(report.bottom <= report.viewport + 1, `composer below viewport: ${JSON.stringify(report)}`)
    assert.ok(report.chatHeight >= 50, `message area too small: ${JSON.stringify(report)}`)
    assert.ok(report.chatOverflows && report.chatScrolls && report.listScrolls && report.independent, JSON.stringify(report))
  })
}
